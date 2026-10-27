#!/usr/bin/env bash
# ==============================================================================
# Setup & Dependency Commands for 100% Offline Edge AI React Native App
# Optimized for low-end hardware (4GB RAM) with llama.rn native C++ bindings
# ==============================================================================

# 1. Initialize Bare React Native Project (TypeScript template)
npx react-native@latest init EdgeAIAssistant --template react-native-template-typescript
cd EdgeAIAssistant

# 2. Install AI Engine (llama.rn - React Native wrapper for llama.cpp)
npm install llama.rn

# 3. Install Document & Image Parsing Libraries
npm install react-native-document-picker
npm install react-native-image-picker
npm install @react-native-ml-kit/text-recognition
npm install react-native-pdf
npm install react-native-blob-util

# 4. Install Local SQLite Database for Vector Storage / Local RAG
npm install react-native-sqlite-storage
npm install --save-dev @types/react-native-sqlite-storage

# 5. Install UI Icons and Safe Area Context
npm install react-native-safe-area-context
npm install react-native-vector-icons
npm install --save-dev @types/react-native-vector-icons

# 6. Android Autolinking & Model Weights Directory Setup
# Download quantized GGUF models (under 2B parameters, Q4_K_M) into assets:
mkdir -p android/app/src/main/assets/models

# Example models to place in android/app/src/main/assets/models/:
# - qwen2.5-1.5b-instruct-q4_k_m.gguf (~986 MB)
# - all-minilm-l6-v2-q4_k_m.gguf (~45 MB)

echo "Dependencies installed successfully! Follow android/app/build.gradle NDK instructions."
