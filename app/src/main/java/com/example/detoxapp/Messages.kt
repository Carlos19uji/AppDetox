package com.example.detoxapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun Messages(navController: NavController, groupViewModel: GroupViewModel){
    Column() {
        Row(){
            Text(text= "Messages")
        }
    }
}