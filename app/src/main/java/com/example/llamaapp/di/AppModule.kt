package com.example.llamaapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.llama.LlamaEngine
import com.example.llama.internal.LlamaEngineImpl
import com.example.llamaapp.data.db.AppDatabase
import com.example.llamaapp.data.db.ConversationDao
import com.example.llamaapp.data.db.MessageDao
import com.example.llamaapp.data.db.ModelDao
import com.example.llamaapp.data.repository.ChatRepository
import com.example.llamaapp.data.repository.ChatRepositoryImpl
import com.example.llamaapp.data.repository.ModelRepository
import com.example.llamaapp.data.repository.ModelRepositoryImpl
import com.example.llamaapp.storage.ModelStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLlamaEngine(@ApplicationContext ctx: Context): LlamaEngine =
        LlamaEngineImpl(ctx)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "llama_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao =
        db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao =
        db.messageDao()

    @Provides
    fun provideModelDao(db: AppDatabase): ModelDao =
        db.modelDao()

    @Provides
    @Singleton
    fun provideModelStorageManager(@ApplicationContext ctx: Context): ModelStorageManager =
        ModelStorageManager(ctx)

    @Provides
    @Singleton
    fun provideChatRepository(dao: ConversationDao, msgDao: MessageDao): ChatRepository =
        ChatRepositoryImpl(dao, msgDao)

    @Provides
    @Singleton
    fun provideModelRepository(dao: ModelDao, storage: ModelStorageManager): ModelRepository =
        ModelRepositoryImpl(dao, storage)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            ctx.preferencesDataStoreFile("settings")
        }
}
