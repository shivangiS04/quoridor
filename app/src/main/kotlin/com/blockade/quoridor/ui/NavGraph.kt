package com.blockade.quoridor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blockade.quoridor.domain.ai.AiDifficulty
import com.blockade.quoridor.ui.game.GameScreen
import com.blockade.quoridor.ui.home.HomeScreen
import com.blockade.quoridor.ui.waiting.WaitingScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {

        composable("home") {
            HomeScreen(
                onRoomCreated = { roomId, code ->
                    nav.navigate("waiting/$roomId/$code")
                },
                onRoomJoined = { roomId, pi ->
                    nav.navigate("game/$roomId/$pi")
                },
                onResumeWaiting = { roomId, code ->
                    nav.navigate("waiting/$roomId/$code")
                },
                onPlayComputer = { difficulty -> nav.navigate("local/${difficulty.name}") },
            )
        }

        composable(
            "local/{difficulty}",
            arguments = listOf(navArgument("difficulty") { type = NavType.StringType }),
        ) { back ->
            val difficulty = AiDifficulty.valueOf(back.arguments!!.getString("difficulty")!!)
            GameScreen(
                roomId = "local",
                myPlayerIndex = 0,
                aiDifficulty = difficulty,
                onLeave = { nav.popBackStack("home", inclusive = false) },
            )
        }

        composable(
            "waiting/{roomId}/{code}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("code") { type = NavType.StringType },
            ),
        ) { back ->
            val roomId = back.arguments!!.getString("roomId")!!
            val code = back.arguments!!.getString("code")!!
            WaitingScreen(
                roomId = roomId,
                code = code,
                onMatchStarted = { pi -> nav.navigate("game/$roomId/$pi") { popUpTo("home") } },
            )
        }

        composable(
            "game/{roomId}/{playerIndex}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("playerIndex") { type = NavType.IntType },
            ),
        ) { back ->
            val roomId = back.arguments!!.getString("roomId")!!
            val pi = back.arguments!!.getInt("playerIndex")
            GameScreen(
                roomId = roomId,
                myPlayerIndex = pi,
                onLeave = { nav.navigate("home") { popUpTo("home") { inclusive = true } } },
            )
        }
    }
}
