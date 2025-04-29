package com.example.detoxapp

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class ManageUserPhases : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    // Obtener los datos del grupo
    fun getGroupData(groupId: String, onResult: (GroupData?) -> Unit) {
        db.collection("groups").document(groupId).get()
            .addOnSuccessListener { document ->
                val groupData = document.toObject(GroupData::class.java)
                onResult(groupData)
            }
    }

    // Verificar si la fase del usuario ha terminado
    fun checkUserPhaseStatus(userId: String, groupId: String, onResult: (Boolean) -> Unit) {
        db.collection("groups").document(groupId).get()
            .addOnSuccessListener { document ->
                val groupData = document.toObject(GroupData::class.java)
                val memberData = groupData?.members?.get(userId)

                if (memberData != null) {
                    val phaseStartDate = memberData.phaseStartDate
                    val phaseDuration = 7 // Asumimos que cada fase dura 7 días. Este valor puede variar según la fase

                    // Calcular el tiempo de finalización de la fase
                    val phaseEndDate = phaseStartDate.seconds + (phaseDuration * 86400) // Convertimos días a segundos
                    val currentDate = Timestamp.now().seconds

                    // Verificar si la fase ha terminado
                    onResult(currentDate >= phaseEndDate)
                }
            }
    }

    // Avanzar a la siguiente fase
    fun advanceUserPhase(userId: String, groupId: String, newPhase: Int) {
        val currentDate = Timestamp.now()

        db.collection("groups").document(groupId)
            .update(
                "members.$userId.phase", newPhase,
                "members.$userId.phaseStartDate", currentDate
            )
            .addOnSuccessListener {
                // Usuario ha avanzado de fase
            }
            .addOnFailureListener {
                // Manejo de error
            }
    }

    // Retroceder fase (si el usuario no cumple los requisitos)
    fun retreatUserPhase(userId: String, groupId: String, newPhase: Int) {
        val currentDate = Timestamp.now()

        db.collection("groups").document(groupId)
            .update(
                "members.$userId.phase", newPhase,
                "members.$userId.phaseStartDate", currentDate
            )
            .addOnSuccessListener {
                // Usuario ha retrocedido de fase
            }
            .addOnFailureListener {
                // Manejo de error
            }
    }
}