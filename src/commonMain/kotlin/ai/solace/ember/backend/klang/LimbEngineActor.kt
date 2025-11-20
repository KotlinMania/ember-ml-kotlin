package ai.solace.ember.backend.klang

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Actor-style dispatcher for limb operations. All operations submitted through this actor
 * are processed on dedicated worker coroutines so callers can `await` completion without
 * blocking threads.
 */
object LimbEngineActor {
    private sealed interface Command {
        val reply: CompletableDeferred<LimbEngine>

        class Add(
            val left: LimbEngine,
            val right: LimbEngine,
            override val reply: CompletableDeferred<LimbEngine>,
        ) : Command

        class Sub(
            val left: LimbEngine,
            val right: LimbEngine,
            override val reply: CompletableDeferred<LimbEngine>,
        ) : Command

        class Mul(
            val left: LimbEngine,
            val right: LimbEngine,
            override val reply: CompletableDeferred<LimbEngine>,
        ) : Command

        class Div(
            val left: LimbEngine,
            val right: LimbEngine,
            override val reply: CompletableDeferred<LimbEngine>,
        ) : Command
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandChannel = Channel<Command>(Channel.UNLIMITED)

    init {
        val workerCount = 4
        repeat(workerCount) {
            scope.launch {
                for (cmd in commandChannel) {
                    when (cmd) {
                        is Command.Add -> cmd.reply.complete(cmd.left.add(cmd.right))
                        is Command.Sub -> cmd.reply.complete(cmd.left.sub(cmd.right))
                        is Command.Mul -> cmd.reply.complete(cmd.left.mul(cmd.right))
                        is Command.Div -> cmd.reply.complete(cmd.left.div(cmd.right))
                    }
                }
            }
        }
    }

    suspend fun add(left: LimbEngine, right: LimbEngine): LimbEngine {
        val deferred = CompletableDeferred<LimbEngine>()
        commandChannel.send(Command.Add(left.copy(), right.copy(), deferred))
        return deferred.await()
    }

    suspend fun subtract(left: LimbEngine, right: LimbEngine): LimbEngine {
        val deferred = CompletableDeferred<LimbEngine>()
        commandChannel.send(Command.Sub(left.copy(), right.copy(), deferred))
        return deferred.await()
    }

    suspend fun multiply(left: LimbEngine, right: LimbEngine): LimbEngine {
        val deferred = CompletableDeferred<LimbEngine>()
        commandChannel.send(Command.Mul(left.copy(), right.copy(), deferred))
        return deferred.await()
    }

    suspend fun divide(left: LimbEngine, right: LimbEngine): LimbEngine {
        val deferred = CompletableDeferred<LimbEngine>()
        commandChannel.send(Command.Div(left.copy(), right.copy(), deferred))
        return deferred.await()
    }
}
