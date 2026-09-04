package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.data.model.AppNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class NotificationRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getNotifications(): List<AppNotification> {
        val user = auth.currentUser ?: return emptyList()

        val snapshot = firestore
            .collection("users")
            .document(user.uid)
            .collection("notifications")
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(AppNotification::class.java)
        }
    }

    suspend fun saveNotification(notification: AppNotification) {
        val user = auth.currentUser ?: return

        firestore
            .collection("users")
            .document(user.uid)
            .collection("notifications")
            .document(notification.id)
            .set(notification)
            .await()
    }
}