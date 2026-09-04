package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.data.model.FeedingEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class HistoryRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** Loads feeding history for the currently logged-in account only, newest first. */
    suspend fun getFeedingHistory(): List<FeedingEvent> {
        val user = auth.currentUser ?: return emptyList()

        val snapshot = firestore
            .collection("users")
            .document(user.uid)
            .collection("history")
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { it.toObject(FeedingEvent::class.java) }
            .sortedByDescending { it.id }
    }

    /** Saves a single feeding event under the currently logged-in account. */
    suspend fun saveFeedingEvent(event: FeedingEvent) {
        val user = auth.currentUser ?: return

        firestore
            .collection("users")
            .document(user.uid)
            .collection("history")
            .document(event.id)
            .set(event)
            .await()
    }
}