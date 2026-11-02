package com.xyrospace.expensetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.xyrospace.expensetracker.sms.DiscardingSmsMessageHandler
import com.xyrospace.expensetracker.sms.SmsMessageHandler
import com.xyrospace.expensetracker.sms.SmsPermissionChecker
import com.xyrospace.expensetracker.sms.SmsPermissionManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {

    @Binds
    @Singleton
    abstract fun bindSmsPermissionChecker(manager: SmsPermissionManager): SmsPermissionChecker

    @Binds
    @Singleton
    abstract fun bindSmsMessageHandler(handler: DiscardingSmsMessageHandler): SmsMessageHandler

    companion object {
        private const val SMS_PREFERENCES_NAME = "sms_preferences"

        @Provides
        @Singleton
        fun provideSmsPreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile(SMS_PREFERENCES_NAME) },
        )
    }
}
