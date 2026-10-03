package com.example.warp

import android.app.Application
import androidx.room.Room
import com.example.warp.data.AppDatabase

class WarpApplication : Application() {
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "warp-database"
        ).build()
    }
}
