import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.event.Logging

/**
 * Created by Jack Daniels on 17.05.2015.
 */
class Game(players: Seq[ActorRef], number:Int) extends Actor {
  import GameProtocol._
  import PlayerProtocol._
  import CounterProtocol._

  val log = Logging(context.system, this)

  players foreach {p => context watch p}

  var finished = false

  def receive = {
    case Broadcast(tick) => {
      if (!finished) {
        if (tick == 3) {
          finished = true
        }
        players foreach { p => p ! Show(s"$tick\r\n") }
      }
    }
    case Start(message) => {
      players foreach {p => p ! GameStart("We have found your opponent. Send me a space when you will see 3! \r\n")}
      val counter = context.actorOf(Counter.counterProps(self),s"counter_$number")
      counter ! Count(s"Starting counter #$number")
      log.info(message)
    }
    case Terminated(player) => {
      log.info(s"Game has watched termination of player ${player.path.toStringWithoutAddress}")
      finished = true
      getAnotherPlayers(player, players) foreach {p => {
          p ! Won("You Have Just Won Cause Your Opponent Have Disconnected!\r\n")
        }
      }
      context stop self
    }
    case InputData(message) => {
      if (!finished || (finished && message != " ")) {
        sender() ! Lost("Sorry! You Have Just Lost.\r\n")
        getAnotherPlayers(sender(),players) foreach { p => p ! Won("You've WON! You opponent have fault started!")}
      } else {
        sender() ! Won("You Have Just Won!\r\n")
        getAnotherPlayers(sender(),players) foreach { p => p ! Lost("You've Lost! You are not so fast.")}
      }
      players foreach {p => p ! Show("Game just have finished! Thanks for participating.\r\n")}
      context stop self
    }
  }

  private def getAnotherPlayers(current: ActorRef, all: Seq[ActorRef]): Seq[ActorRef] = {
    all filterNot {p => p.path.toStringWithoutAddress == current.path.toStringWithoutAddress}
  }
}

object Game {
  def gameProps(player1: ActorRef, player2: ActorRef, number: Int): Props = Props(new Game(Seq(player1, player2), number))
}

object GameProtocol {
  case class Start(message: String)
  case class Won(message: String)
  case class Lost(message: String)
}