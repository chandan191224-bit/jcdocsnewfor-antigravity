package com.example.db

import kotlinx.coroutines.flow.Flow

class DocRepository(private val docDao: DocDao) {
    val allDocuments: Flow<List<DocEntity>> = docDao.getAllDocuments()

    fun getDocumentById(id: Int): Flow<DocEntity?> = docDao.getDocumentById(id)

    fun getDocumentsByType(type: String): Flow<List<DocEntity>> = docDao.getDocumentsByType(type)

    fun searchDocuments(query: String): Flow<List<DocEntity>> = docDao.searchDocuments(query)

    suspend fun insertDocument(document: DocEntity): Long {
        return docDao.insertDocument(document)
    }

    suspend fun deleteDocument(document: DocEntity) {
        docDao.deleteDocument(document)
    }

    suspend fun deleteDocumentById(id: Int) {
        docDao.deleteDocumentById(id)
    }
}
