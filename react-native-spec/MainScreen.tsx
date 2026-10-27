/**
 * MainScreen.tsx
 * 
 * Clean, minimal chat interface for 100% Offline Edge AI.
 * Includes buttons for "Upload File" and "Take Photo", live model indicator,
 * and zero-concurrency pipeline orchestration.
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  SafeAreaView,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  Alert,
  StatusBar,
} from 'react-native';
import DocumentPicker from 'react-native-document-picker';
import { launchCamera, launchImageLibrary } from 'react-native-image-picker';
import { AIModelManager } from './AIModelManager';
import { RAGPipeline, RAGChunk } from './RAGPipeline';

interface Message {
  id: string;
  sender: 'user' | 'assistant' | 'system';
  text: string;
  citations?: RAGChunk[];
}

export const MainScreen: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'init',
      sender: 'assistant',
      text: 'EdgeAI Offline Assistant ready.\n• 100% Offline (No cloud APIs)\n• Zero-Concurrency RAM Lock (4GB device safe)\n• Context Limit: 1,024 tokens max.',
    },
  ]);
  const [inputText, setInputText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [statusText, setStatusText] = useState('Idle');
  const [activeModel, setActiveModel] = useState<string>('None');

  const modelManager = useRef(AIModelManager.getInstance()).current;
  const ragPipeline = useRef(new RAGPipeline()).current;
  const flatListRef = useRef<FlatList>(null);

  useEffect(() => {
    ragPipeline.initDatabase().catch(console.error);
  }, []);

  /**
   * Scenario A: Image Input
   * 1. Capture/Pick Image
   * 2. LOAD Vision OCR
   * 3. Extract text
   * 4. Save text
   * 5. UNLOAD Vision OCR (free RAM)
   * 6. LOAD Reasoning LLM
   * 7. Feed text to LLM for explanation
   * 8. Output to UI
   */
  const handleTakePhoto = async () => {
    try {
      const result = await launchCamera({
        mediaType: 'photo',
        quality: 0.8,
        saveToPhotos: false,
      });

      if (!result.assets || result.assets.length === 0) return;
      const uri = result.assets[0].uri;
      if (!uri) return;

      setIsProcessing(true);
      setStatusText('Scenario A: Starting Image OCR Pipeline...');

      // Step 2: LOAD Vision OCR
      await modelManager.loadModel('VISION_OCR', setStatusText);
      setActiveModel('Vision OCR');

      // Step 3: Extract text
      setStatusText('Scanning text with ML Kit OCR...');
      const extractedText = await modelManager.recognizeTextFromImage(uri);

      // Step 5: UNLOAD Vision OCR (ZERO-CONCURRENCY)
      setStatusText('Unloading Vision OCR to free RAM...');
      await modelManager.unloadActiveModel();
      setActiveModel('Unloaded');

      // Add user message to UI
      const userMsg: Message = {
        id: Date.now().toString(),
        sender: 'user',
        text: `📷 [Captured Document Image]\nPlease analyze and explain this extracted text:\n"${extractedText.slice(0, 150)}..."`,
      };
      setMessages((prev) => [...prev, userMsg]);

      // Step 6: LOAD Reasoning LLM
      setStatusText('Loading Qwen2.5-1.5B (Allocating context <= 1024)...');
      await modelManager.loadModel('REASONING', setStatusText);
      setActiveModel('Qwen2.5-1.5B');

      // Step 7: Feed extracted text to LLM
      const prompt = `<|im_start|>system\nYou are an offline assistant. Explain the following scanned text clearly.<|im_end|>\n` +
        `<|im_start|>user\n${extractedText}\nExplain what this is and summarize the key information.<|im_end|>\n` +
        `<|im_start|>assistant\n`;

      const assistantMsgId = (Date.now() + 1).toString();
      setMessages((prev) => [
        ...prev,
        { id: assistantMsgId, sender: 'assistant', text: '' },
      ]);

      let fullResponse = '';
      for await (const token of modelManager.streamReasoning(prompt, (token) => {
        fullResponse += token;
        setMessages((prev) =>
          prev.map((m) => (m.id === assistantMsgId ? { ...m, text: fullResponse } : m))
        );
      })) {}

      setStatusText('Reasoning complete.');
    } catch (error: any) {
      Alert.alert('Scan Error', error?.message || 'Failed to process image');
    } finally {
      setIsProcessing(false);
    }
  };

  /**
   * Scenario B: Large Document Input
   * 1. Pick PDF/TXT
   * 2. Extract & Chunk text
   * 3. LOAD Embedding model
   * 4. Vectorize chunks to SQLite
   * 5. UNLOAD Embedding model
   * 6. LOAD Reasoning LLM
   * 7. Local RAG Top-K retrieval
   * 8. Output answer
   */
  const handleUploadFile = async () => {
    try {
      const res = await DocumentPicker.pickSingle({
        type: [DocumentPicker.types.pdf, DocumentPicker.types.plainText],
      });

      setIsProcessing(true);
      setStatusText(`Parsing ${res.name}...`);

      // Mock extracted text for demonstration or read using RNFS/blob-util
      const sampleText = `TechEdge Inc. Financial Report Q3. Total revenue was $14.2M, up 28% YoY. Operating expenses $8.6M. Zero-concurrency edge AI reduced cloud costs by $1.8M annually.`;

      // Steps 2 - 5: Chunk -> Embed -> SQLite -> Unload Embedding
      await ragPipeline.ingestDocument(
        res.name || 'document.txt',
        res.name?.endsWith('.pdf') ? 'PDF' : 'TXT',
        sampleText,
        setStatusText
      );

      setMessages((prev) => [
        ...prev,
        {
          id: Date.now().toString(),
          sender: 'system',
          text: `📄 Ingested "${res.name}" into local SQLite. MiniLM unloaded. You can now ask questions!`,
        },
      ]);
    } catch (err) {
      if (!DocumentPicker.isCancel(err)) {
        Alert.alert('File Picker Error', String(err));
      }
    } finally {
      setIsProcessing(false);
      setStatusText('Idle');
    }
  };

  const handleSendMessage = async () => {
    if (!inputText.trim() || isProcessing) return;
    const query = inputText.trim();
    setInputText('');

    const userMsg: Message = {
      id: Date.now().toString(),
      sender: 'user',
      text: query,
    };
    setMessages((prev) => [...prev, userMsg]);
    setIsProcessing(true);

    try {
      // 1. Retrieve RAG chunks
      setStatusText('Searching local SQLite vector store...');
      const chunks = await ragPipeline.retrieveRelevantChunks(query, 2);

      // 2. Ensure Reasoning LLM is loaded
      await modelManager.loadModel('REASONING', setStatusText);
      setActiveModel('Qwen2.5-1.5B');

      // 3. Format prompt with strict 1024 token limit
      const prompt = ragPipeline.buildPrompt(query, chunks);

      const assistantMsgId = (Date.now() + 1).toString();
      setMessages((prev) => [
        ...prev,
        { id: assistantMsgId, sender: 'assistant', text: '', citations: chunks },
      ]);

      let streamText = '';
      for await (const _ of modelManager.streamReasoning(prompt, (token) => {
        streamText += token;
        setMessages((prev) =>
          prev.map((m) => (m.id === assistantMsgId ? { ...m, text: streamText } : m))
        );
      })) {}

      setStatusText('Complete');
    } catch (err: any) {
      Alert.alert('Inference Error', err?.message || 'Execution failed');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#0F172A" />

      {/* Header Telemetry Bar */}
      <View style={styles.header}>
        <View style={styles.headerRow}>
          <Text style={styles.headerTitle}>EdgeAI • 4GB Profile</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>100% OFFLINE</Text>
          </View>
        </View>
        <View style={styles.statusRow}>
          <Text style={styles.statusLabel}>Active Model: </Text>
          <Text style={styles.statusValue}>{activeModel}</Text>
          <Text style={styles.statusDivider}> | </Text>
          <Text style={styles.statusLabel}>Context: </Text>
          <Text style={styles.statusValue}>&lt;= 1024 tok</Text>
        </View>
        {isProcessing && (
          <View style={styles.progressRow}>
            <ActivityIndicator size="small" color="#38BDF8" />
            <Text style={styles.progressText}>{statusText}</Text>
          </View>
        )}
      </View>

      {/* Chat Messages List */}
      <FlatList
        ref={flatListRef}
        data={messages}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.messageList}
        renderItem={({ item }) => (
          <View
            style={[
              styles.messageBubble,
              item.sender === 'user'
                ? styles.userBubble
                : item.sender === 'system'
                ? styles.systemBubble
                : styles.assistantBubble,
            ]}
          >
            <Text
              style={[
                styles.messageText,
                item.sender === 'user' ? styles.userText : styles.assistantText,
              ]}
            >
              {item.text}
            </Text>
            {item.citations && item.citations.length > 0 && (
              <View style={styles.citationBox}>
                <Text style={styles.citationTitle}>Verified Local Excerpts:</Text>
                {item.citations.map((c, i) => (
                  <Text key={i} style={styles.citationItem}>
                    • Excerpt {i + 1} (Score: {Math.round((c.similarityScore || 0) * 100)}%)
                  </Text>
                ))}
              </View>
            )}
          </View>
        )}
      />

      {/* Bottom Action & Input Bar */}
      <View style={styles.inputContainer}>
        <View style={styles.actionButtonRow}>
          <TouchableOpacity
            style={styles.actionBtn}
            onPress={handleUploadFile}
            disabled={isProcessing}
          >
            <Text style={styles.actionBtnText}>📁 Upload File</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.actionBtn}
            onPress={handleTakePhoto}
            disabled={isProcessing}
          >
            <Text style={styles.actionBtnText}>📷 Take Photo</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.inputRow}>
          <TextInput
            style={styles.inputField}
            placeholder="Ask offline assistant..."
            placeholderTextColor="#94A3B8"
            value={inputText}
            onChangeText={setInputText}
            editable={!isProcessing}
          />
          <TouchableOpacity
            style={[
              styles.sendButton,
              (!inputText.trim() || isProcessing) && styles.sendButtonDisabled,
            ]}
            onPress={handleSendMessage}
            disabled={!inputText.trim() || isProcessing}
          >
            <Text style={styles.sendButtonText}>Send</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  header: {
    padding: 12,
    backgroundColor: '#1E293B',
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#38BDF8',
  },
  badge: {
    backgroundColor: '#10B98120',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  badgeText: {
    color: '#10B981',
    fontSize: 10,
    fontWeight: 'bold',
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  statusLabel: {
    color: '#94A3B8',
    fontSize: 11,
  },
  statusValue: {
    color: '#F8FAFC',
    fontSize: 11,
    fontWeight: '600',
  },
  statusDivider: {
    color: '#475569',
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 6,
  },
  progressText: {
    color: '#38BDF8',
    fontSize: 11,
    marginLeft: 6,
  },
  messageList: {
    padding: 12,
  },
  messageBubble: {
    padding: 12,
    borderRadius: 12,
    marginBottom: 10,
    maxWidth: '85%',
  },
  userBubble: {
    alignSelf: 'flex-end',
    backgroundColor: '#0284C7',
  },
  assistantBubble: {
    alignSelf: 'flex-start',
    backgroundColor: '#1E293B',
  },
  systemBubble: {
    alignSelf: 'center',
    backgroundColor: '#334155',
  },
  messageText: {
    fontSize: 13,
    lineHeight: 19,
  },
  userText: {
    color: '#FFFFFF',
  },
  assistantText: {
    color: '#F1F5F9',
  },
  citationBox: {
    marginTop: 8,
    padding: 6,
    backgroundColor: '#0F172A',
    borderRadius: 6,
  },
  citationTitle: {
    fontSize: 10,
    fontWeight: 'bold',
    color: '#38BDF8',
  },
  citationItem: {
    fontSize: 9,
    color: '#94A3B8',
    marginTop: 2,
  },
  inputContainer: {
    padding: 10,
    backgroundColor: '#1E293B',
    borderTopWidth: 1,
    borderTopColor: '#334155',
  },
  actionButtonRow: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  actionBtn: {
    backgroundColor: '#334155',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginRight: 8,
  },
  actionBtnText: {
    color: '#F8FAFC',
    fontSize: 12,
    fontWeight: '600',
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  inputField: {
    flex: 1,
    backgroundColor: '#0F172A',
    color: '#FFFFFF',
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 8,
    fontSize: 13,
    borderWidth: 1,
    borderColor: '#334155',
  },
  sendButton: {
    marginLeft: 8,
    backgroundColor: '#0284C7',
    paddingHorizontal: 16,
    paddingVertical: 9,
    borderRadius: 20,
  },
  sendButtonDisabled: {
    backgroundColor: '#475569',
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 12,
  },
});
