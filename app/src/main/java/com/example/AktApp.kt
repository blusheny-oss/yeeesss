package com.example

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Приложение умеет работать без настоящего google-services.json:
 * если конфигурация не подставлена, Firebase поднимается с локальной
 * заглушкой, а данные живут в офлайн-кэше Firestore на телефоне.
 */
class AktApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            runCatching {
                FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setApplicationId("1:000000000000:android:aktzhilya-local")
                        .setApiKey("local-only")
                        .setProjectId("aktzhilya-local")
                        .build(),
                )
            }
        }
    }
}
