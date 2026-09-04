package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.data.model.FeedingSchedule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ScheduleRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun schedulesCollection() =
        firestore
            .collection("users")
            .document(auth.currentUser?.uid ?: "")
            .collection("schedules")

    suspend fun getFeedingSchedules(): List<FeedingSchedule> {
        val user = auth.currentUser ?: return emptyList()

        val snapshot = firestore
            .collection("users")
            .document(user.uid)
            .collection("schedules")
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(FeedingSchedule::class.java)
        }
    }

    suspend fun saveFeedingSchedule(schedule: FeedingSchedule) {
        val user = auth.currentUser ?: return

        firestore
            .collection("users")
            .document(user.uid)
            .collection("schedules")
            .document(schedule.id)
            .set(schedule)
            .await()
    }

    /** Deletes a single feeding schedule for the currently logged-in account. */
    suspend fun deleteFeedingSchedule(scheduleId: String) {
        val user = auth.currentUser ?: return

        firestore
            .collection("users")
            .document(user.uid)
            .collection("schedules")
            .document(scheduleId)
            .delete()
            .await()
    }
}