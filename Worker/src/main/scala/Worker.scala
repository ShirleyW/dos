

import akka.actor.Actor
import akka.actor.Props
import akka.routing.RoundRobinRouter
import akka.actor.ActorSystem


trait Message
case class Work(uf: String, k: Int, start: Long, nrOfElements: Long) extends Message
case class Result(bitcoin: String, hash: String) extends Message
case object AskForTask extends Message
case object Start extends Message
case object Stop extends Message
case object WorkDone extends Message

class Worker extends Actor {
    
	def map(num: Long):String = {
      var s = (32 + num % 95).toChar.toString
      if (num / 95 == 0)  s else s + map(num / 95)
    }
 
    def receive = {
 
      case Work(uf, k, start, nrOfElements) =>
        println("get work")
        for (i <- start until nrOfElements + start) {
          var s = uf + map(i)
          var sha256hex = org.apache.commons.codec.digest.DigestUtils.sha256Hex(s);
          try {
        	  val foo = Integer.parseInt(sha256hex.substring(0, k));
        	  if (foo == 0) {
        	    sender ! Result(s, sha256hex)
        	  }
          } 
          catch {
          	case e:NumberFormatException => ;
          }
        }
        sender ! WorkDone
        context.stop(self)
    }
  }

class WorkerMaster(ip: String, nrOfWorkers: Int) extends Actor {
  val masterRef = "akka.tcp://Master@" + ip + ":2666/user/MasterActor"
  val master = context.actorFor(masterRef)
  var doneWorker = 0
  def receive = {
      case Start => 
        master ! AskForTask

      case Work(uf, k, start, nrOfElements) =>
        for (i <- 0 until nrOfWorkers) {
          val worker = context.actorOf(Props[Worker], name = "worker"+i)
          worker ! Work(uf, k, start + i * (nrOfElements/nrOfWorkers), nrOfElements/nrOfWorkers)
        }
        
      case Result(bitcoin, hash) =>
        master ! Result(bitcoin, hash)
        
      case WorkDone => 
        doneWorker = doneWorker + 1
        if (doneWorker == nrOfWorkers) {
            master ! AskForTask
            doneWorker = 0
        }
        println("Done")        
      case Stop => 
        println("stop")
        context.stop(self)
        context.system.shutdown()
  }  
}

object Worker {
	def main(args: Array[String]) {
    val ip = if (args.length > 0) args(0)  else "128.227.248.195"
    val system = ActorSystem("Worker")
    val nrOfWorkers = 2
    val workerM = system.actorOf(Props(new WorkerMaster(ip, nrOfWorkers)), name = "worker")
    workerM ! Start
  }
}
