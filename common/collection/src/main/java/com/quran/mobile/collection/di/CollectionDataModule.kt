package com.quran.mobile.collection.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.collection.CollectionsDatabase
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
class CollectionDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideCollectionsDatabase(@ApplicationContext context: Context): CollectionsDatabase {
    return CollectionsDatabase(
      AndroidSqliteDriver(CollectionsDatabase.Schema, context, "collections.db")
    )
  }
}
