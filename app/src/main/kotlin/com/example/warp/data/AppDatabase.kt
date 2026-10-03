package com.example.warp.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Setting::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingDao(): SettingDao
}
