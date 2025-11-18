from flask import Flask, request, jsonify
import chromadb
from langchain_community.llms import LlamaCpp
from langchain.embeddings import LlamaCppEmbeddings
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import PyPDFLoader
from langchain.vectorstores import Chroma
import os

# Initialize Flask app
app = Flask(__name__)

# Initialize ChromaDB client for persistence
client = chromadb.PersistentClient(path="services/rag/db")

# Initialize Llama3 model
model_path = os.environ.get("LLAMA3_MODEL_PATH", "/models/llama3/llama-3-8b-instruct.Q4_K_M.gguf")
llm = LlamaCpp(
    model_path=model_path,
    temperature=0.75,
    max_tokens=2000,
    top_p=1,
    verbose=True,
)
# Initialize embeddings
embeddings = LlamaCppEmbeddings(model_path=model_path)

# Initialize text splitter
text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=0)

# Create or load a collection
collection_name = "demeter_rag"
collection = client.get_or_create_collection(name=collection_name)

# Create Chroma vector store
vectorstore = Chroma(client=client, collection_name=collection_name, embedding_function=embeddings)

@app.route('/add', methods=['POST'])
def add_data():
    if 'file' in request.files:
        file = request.files['file']
        if file.filename.endswith('.pdf'):
            # Save the PDF to a temporary file
            filepath = os.path.join('/tmp', file.filename)
            file.save(filepath)

            # Load and split the PDF
            loader = PyPDFLoader(filepath)
            documents = loader.load_and_split(text_splitter)

            # Add documents to the vector store
            vectorstore.add_documents(documents)

            # Clean up the temporary file
            os.remove(filepath)

            return jsonify({'message': 'PDF data added successfully'})

    # Get data from request
    data = request.get_json()
    text = data.get('text')
    if text:
        # Split text into chunks
        chunks = text_splitter.split_text(text)

        # Add chunks to vector store
        vectorstore.add_texts(chunks)

        return jsonify({'message': 'Text data added successfully'})

    return jsonify({'message': 'No data provided'}), 400

@app.route('/query', methods=['GET'])
def query_data():
    # Get query from request
    query = request.args.get('query')

    # Search for similar documents in vector store
    docs = vectorstore.similarity_search(query)

    # Generate response with Llama3 model
    response = llm.invoke(f"Context: {docs}\n\nQuestion: {query}")

    return jsonify({'response': response})

if __name__ == '__main__':
    app.run(debug=True)
