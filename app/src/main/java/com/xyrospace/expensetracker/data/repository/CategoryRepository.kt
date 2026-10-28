package com.xyrospace.expensetracker.data.repository

import com.xyrospace.expensetracker.data.local.CategoryDao
import com.xyrospace.expensetracker.data.local.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getAll(): List<CategoryEntity> = categoryDao.getAll()

    suspend fun getByName(name: String): CategoryEntity? = categoryDao.getByName(name)

    suspend fun insert(category: CategoryEntity): Long = categoryDao.insert(category)

    suspend fun update(category: CategoryEntity): Int = categoryDao.update(category)

    suspend fun delete(id: Long): Int = categoryDao.deleteById(id)
}
