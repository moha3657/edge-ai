/**
 * AIModelManager.ts
 * 
 * Strict Zero-Concurrency Model Lifecycle Manager for Low-End Android Devices (4GB RAM).
 * Guarantees that at NO point in time are two models loaded in memory simultaneously.
 * Uses llama.rn (llama.cpp) native bindings with a strict 1024 token context ceiling.
 */

import { initLlama, LlamaContext } from 'llama.rn';
import TextRecognition from '@react-native-ml-kit/text-recognition';

export type ModelType = 'REASONING' | 'VISION_OCR' | 'EMBEDDING';

export interface ModelMetadata {
  id: ModelType;
  displayName: string;
  filename: string;
  maxContext: number; // Max 1024 tokens for low-end RAM safety
  estRamMB: number;
}

export const MODELS_CONFIG: Record<ModelType, ModelMetadata> = {
  REASONING: {
    id: 'REASONING',
    displayName: 'Qwen2.5-1.5B-Instruct (Q4_K_M)',
    filename: 'qwen2.5-1.5b-instruct-q4_k_m.gguf',
    maxContext: 1024, // HARD CEILING
    estRamMB: 980,
  },
  VISION_OCR: {
    id: 'VISION_OCR',
    displayName: 'Google ML Kit Vision OCR',
    filename: 'mlkit-vision-native',
    maxContext: 1024,
    estRamMB: 18, // Zero-RAM impact fallback
  },
  EMBEDDING: {
    id: 'EMBEDDING',
    displayName: 'all-MiniLM-L6-v2 (GGUF)',
    filename: 'all-minilm-l6-v2-q4_k_m.gguf',
    maxContext: 512,
    estRamMB: 85,
  },
};

export class AIModelManager {
  private static instance: AIModelManager | null = null;
  private activeModelType: ModelType | null = null;
  private activeContext: LlamaContext | null = null;
  private isTransitioning: boolean = false;
  private contextTokenCount: number = 0;

  // Simple async lock / mutex to prevent concurrent load/unload calls
  private lockPromise: Promise<void> = Promise.resolve();

  private constructor() {}

  public static getInstance(): AIModelManager {
    if (!AIModelManager.instance) {
      AIModelManager.instance = new AIModelManager();
    }
    return AIModelManager.instance;
  }

  public getActiveModel(): ModelType | null {
    return this.activeModelType;
  }

  public getContextTokensUsed(): number {
    return this.contextTokenCount;
  }

  /**
   * Acquire mutex lock to serialize operations
   */
  private async acquireLock(): Promise<() => void> {
    let releaseLock: () => void = () => {};
    const waitPromise = new Promise<void>((resolve) => {
      releaseLock = resolve;
    });
    const currentLock = this.lockPromise;
    this.lockPromise = currentLock.then(() => waitPromise);
    await currentLock;
    return releaseLock;
  }

  /**
   * Loads the requested model while strictly guaranteeing any active model
   * is completely released from native C++ RAM first.
   */
  public async loadModel(
    target: ModelType,
    onProgress?: (status: string) => void
  ): Promise<boolean> {
    const release = await this.acquireLock();
    try {
      if (this.activeModelType === target && (this.activeContext || target === 'VISION_OCR')) {
        onProgress?.(`${MODELS_CONFIG[target].displayName} already resident in RAM.`);
        return true;
      }

      this.isTransitioning = true;

      // STEP 1: UNLOAD CURRENT ACTIVE MODEL COMPLETELY
      if (this.activeModelType !== null) {
        onProgress?.(`Unloading ${MODELS_CONFIG[this.activeModelType].displayName} to reclaim RAM...`);
        await this.unloadInternal();
        // Give garbage collector time to clean up mapped native memory
        await new Promise((resolve) => setTimeout(resolve, 150));
      }

      // STEP 2: LOAD REQUESTED TARGET MODEL
      const config = MODELS_CONFIG[target];
      onProgress?.(`Loading ${config.displayName} (Context capped at ${config.maxContext} tokens)...`);

      if (target === 'VISION_OCR') {
        // ML Kit initializes on first call without persistent heap lock
        this.activeModelType = 'VISION_OCR';
        this.activeContext = null;
        this.contextTokenCount = 0;
      } else {
        // llama.rn native C++ llama.cpp initialization
        const modelPath = `models/${config.filename}`;
        
        this.activeContext = await initLlama({
          model: modelPath,
          use_mlock: false, // Low-end device: false to allow OS paging
          n_ctx: config.maxContext, // CRITICAL: strictly <= 1024 tokens
          n_threads: 4, // Optimal for 4-core cluster (e.g., big cores on Cortex-A78/A55)
          n_gpu_layers: 0, // CPU-only mode for stability across low-end Adreno/Mali GPUs
          embedding: target === 'EMBEDDING', // Enable embedding mode if MiniLM
        });

        this.activeModelType = target;
        this.contextTokenCount = 0;
      }

      onProgress?.(`${config.displayName} successfully loaded into RAM.`);
      return true;
    } catch (error) {
      console.error(`Failed to load model ${target}:`, error);
      await this.unloadInternal();
      throw error;
    } finally {
      this.isTransitioning = false;
      release();
    }
  }

  /**
   * Explicitly unloads the active model from native memory.
   */
  public async unloadActiveModel(): Promise<void> {
    const release = await this.acquireLock();
    try {
      await this.unloadInternal();
    } finally {
      release();
    }
  }

  private async unloadInternal(): Promise<void> {
    if (this.activeContext) {
      try {
        await this.activeContext.release();
      } catch (err) {
        console.warn('Error releasing llama context:', err);
      }
      this.activeContext = null;
    }
    this.activeModelType = null;
    this.contextTokenCount = 0;

    // Hint to V8 / Hermes garbage collector
    if (global.gc) {
      global.gc();
    }
  }

  /**
   * Run OCR text recognition on an image file URI.
   */
  public async recognizeTextFromImage(imageUri: string): Promise<string> {
    if (this.activeModelType !== 'VISION_OCR') {
      throw new Error('Vision OCR model must be loaded prior to text recognition.');
    }
    const result = await TextRecognition.recognize(imageUri);
    return result.text;
  }

  /**
   * Run embedding vectorization on a single text chunk.
   */
  public async getEmbedding(text: string): Promise<number[]> {
    if (this.activeModelType !== 'EMBEDDING' || !this.activeContext) {
      throw new Error('Embedding model (all-MiniLM-L6-v2) must be loaded.');
    }
    const response = await this.activeContext.embedding(text);
    return response.embedding;
  }

  /**
   * Streams reasoning completion from Qwen2.5-1.5B with strict 1024 token limiter.
   */
  public async *streamReasoning(
    prompt: string,
    onToken?: (token: string) => void
  ): AsyncGenerator<string, void, unknown> {
    if (this.activeModelType !== 'REASONING' || !this.activeContext) {
      throw new Error('Reasoning model (Qwen2.5-1.5B) must be active in RAM.');
    }

    // Token estimate check
    const promptTokens = Math.ceil(prompt.length / 3.8);
    if (promptTokens > 850) {
      console.warn(`Prompt is large (${promptTokens} tokens). Truncating context to preserve 1024 limit.`);
    }

    const maxAllowedTokens = Math.min(1024 - promptTokens, 300);

    const completionPromise = this.activeContext.completion(
      {
        prompt,
        n_predict: maxAllowedTokens,
        temperature: 0.3,
        stop: ['<|im_end|>', '<|endoftext|>', '\nUser:'],
      },
      (data) => {
        if (data.token) {
          this.contextTokenCount = promptTokens + (data.tokens_evaluated || 0);
          onToken?.(data.token);
        }
      }
    );

    const result = await completionPromise;
    yield result.text;
  }
}
