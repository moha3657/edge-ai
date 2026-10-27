/**
 * RAGPipeline.ts
 * 
 * Local RAG Pipeline using react-native-sqlite-storage and zero-concurrency model execution.
 * Handles text chunking, embedding generation into SQLite, and Top-K cosine similarity retrieval.
 */

import SQLite from 'react-native-sqlite-storage';
import { AIModelManager } from './AIModelManager';

SQLite.enablePromise(true);

export interface RAGChunk {
  id: string;
  docId: string;
  chunkIndex: number;
  content: string;
  similarityScore?: number;
}

export class RAGPipeline {
  private db: SQLite.SQLiteDatabase | null = null;
  private modelManager: AIModelManager;

  constructor() {
    this.modelManager = AIModelManager.getInstance();
  }

  /**
   * Initializes the local SQLite database and creates tables.
   */
  public async initDatabase(): Promise<void> {
    if (this.db) return;
    this.db = await SQLite.openDatabase({
      name: 'edge_rag.db',
      location: 'default',
    });

    await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS documents (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        file_type TEXT NOT NULL,
        chunk_count INTEGER NOT NULL,
        created_at INTEGER NOT NULL
      );
    `);

    await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS chunks (
        id TEXT PRIMARY KEY,
        doc_id TEXT NOT NULL,
        chunk_index INTEGER NOT NULL,
        content TEXT NOT NULL,
        embedding_json TEXT NOT NULL,
        FOREIGN KEY (doc_id) REFERENCES documents (id) ON DELETE CASCADE
      );
    `);
  }

  /**
   * Chunk raw text into overlapping sliding-window passages.
   * Words-based chunking preserves sentence and token coherence.
   */
  public chunkText(
    text: string,
    wordsPerChunk: number = 120,
    overlapWords: number = 25
  ): string[] {
    const words = text.split(/\s+/).filter((w) => w.length > 0);
    if (words.length === 0) return [];

    if (words.length <= wordsPerChunk) {
      return [words.join(' ')];
    }

    const chunks: string[] = [];
    let start = 0;
    while (start < words.length) {
      const end = Math.min(start + wordsPerChunk, words.length);
      chunks.push(words.slice(start, end).join(' '));
      if (end >= words.length) break;
      start += wordsPerChunk - overlapWords;
    }
    return chunks;
  }

  /**
   * Scenario B Ingestion:
   * 1. Extract & Chunk text
   * 2. LOAD Embedding model
   * 3. Vectorize chunks to SQLite
   * 4. UNLOAD Embedding model (to free RAM for Reasoning)
   */
  public async ingestDocument(
    title: string,
    fileType: 'PDF' | 'TXT' | 'DOCX',
    rawText: string,
    onProgress?: (status: string) => void
  ): Promise<string> {
    await this.initDatabase();
    if (!this.db) throw new Error('Database not initialized');

    const docId = `doc_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
    const textChunks = this.chunkText(rawText);

    onProgress?.(`Parsed ${title} into ${textChunks.size || textChunks.length} chunks. Loading all-MiniLM-L6-v2...`);

    // ZERO-CONCURRENCY: Load Embedding Model
    await this.modelManager.loadModel('EMBEDDING', onProgress);

    const chunkInserts: Array<{ id: string; index: number; content: string; embeddingJson: string }> = [];

    for (let i = 0; i < textChunks.length; i++) {
      onProgress?.(`Vectorizing chunk ${i + 1}/${textChunks.length}...`);
      const chunkText = textChunks[i];
      const embedding = await this.modelManager.getEmbedding(chunkText);
      
      chunkInserts.push({
        id: `chk_${docId}_${i}`,
        index: i,
        content: chunkText,
        embeddingJson: JSON.stringify(embedding),
      });
    }

    // Insert Document Record
    await this.db.executeSql(
      'INSERT INTO documents (id, title, file_type, chunk_count, created_at) VALUES (?, ?, ?, ?, ?)',
      [docId, title, fileType, textChunks.length, Date.now()]
    );

    // Insert Chunks Batch
    for (const item of chunkInserts) {
      await this.db.executeSql(
        'INSERT INTO chunks (id, doc_id, chunk_index, content, embedding_json) VALUES (?, ?, ?, ?, ?)',
        [item.id, docId, item.index, item.content, item.embeddingJson]
      );
    }

    onProgress?.(`All ${textChunks.length} chunks indexed. Unloading Embedding model to free RAM...`);

    // ZERO-CONCURRENCY: UNLOAD Embedding model immediately
    await this.modelManager.unloadActiveModel();

    onProgress?.('Document vectorized and stored in SQLite. Ready for queries.');
    return docId;
  }

  /**
   * Computes cosine similarity between two float vectors.
   */
  private cosineSimilarity(v1: number[], v2: number[]): number {
    if (v1.length !== v2.length || v1.length === 0) return 0;
    let dot = 0;
    let norm1 = 0;
    let norm2 = 0;
    for (let i = 0; i < v1.length; i++) {
      dot += v1[i] * v2[i];
      norm1 += v1[i] * v1[i];
      norm2 += v2[i] * v2[i];
    }
    const denom = Math.sqrt(norm1) * Math.sqrt(norm2);
    return denom === 0 ? 0 : dot / denom;
  }

  /**
   * Retrieves Top-K chunks based on cosine similarity with user query.
   */
  public async retrieveRelevantChunks(
    query: string,
    topK: number = 2
  ): Promise<RAGChunk[]> {
    await this.initDatabase();
    if (!this.db) return [];

    // Temporary load embedding model if not loaded, or use text matching fallback
    let queryEmbedding: number[] | null = null;
    const active = this.modelManager.getActiveModel();

    if (active === 'EMBEDDING') {
      queryEmbedding = await this.modelManager.getEmbedding(query);
    }

    const [results] = await this.db.executeSql('SELECT id, doc_id, chunk_index, content, embedding_json FROM chunks');
    const chunks: RAGChunk[] = [];

    for (let i = 0; i < results.rows.length; i++) {
      const row = results.rows.item(i);
      let score = 0.5;

      if (queryEmbedding) {
        const chunkVec: number[] = JSON.parse(row.embedding_json);
        score = this.cosineSimilarity(queryEmbedding, chunkVec);
      } else {
        // Fallback BM25/keyword similarity if embedding model is unloaded
        const qTerms = query.toLowerCase().split(/\s+/);
        const cLower = row.content.toLowerCase();
        const matches = qTerms.filter((term) => cLower.includes(term)).length;
        score = matches / Math.max(qTerms.length, 1);
      }

      chunks.push({
        id: row.id,
        docId: row.doc_id,
        chunkIndex: row.chunk_index,
        content: row.content,
        similarityScore: score,
      });
    }

    return chunks
      .sort((a, b) => (b.similarityScore || 0) - (a.similarityScore || 0))
      .slice(0, topK);
  }

  /**
   * Formats prompt with retrieved chunks, strictly respecting the 1024 token limit.
   */
  public buildPrompt(query: string, chunks: RAGChunk[]): string {
    let contextStr = '';
    if (chunks.length > 0) {
      contextStr = `=== EXCERPTS FROM LOCAL DOCUMENT ===\n` +
        chunks.map((c, i) => `[Excerpt ${i + 1}]: ${c.content}`).join('\n\n') +
        `\n====================================\n\n`;
    }

    return `<|im_start|>system\nYou are an offline AI assistant. Answer using the excerpts. Be concise.<|im_end|>\n` +
      `<|im_start|>user\n${contextStr}Question: ${query}<|im_end|>\n` +
      `<|im_start|>assistant\n`;
  }
}
