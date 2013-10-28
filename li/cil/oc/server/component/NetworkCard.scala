package li.cil.oc.server.component

import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.api.network.environment.{Arguments, Context, LuaCallback}
import net.minecraft.nbt.{NBTTagInt, NBTTagList, NBTTagCompound}
import scala.collection.convert.WrapAsScala._
import scala.collection.mutable

class NetworkCard extends ManagedComponent {
  val node = api.Network.createComponent(api.Network.createNode(this, "network", Visibility.Network))
  node.setVisibility(Visibility.Neighbors)

  private val openPorts = mutable.Set.empty[Int]

  // ----------------------------------------------------------------------- //

  @LuaCallback("open")
  def open(context: Context, args: Arguments): Array[Object] = {
    val port = checkPort(args.checkInteger(1))
    result(openPorts.add(port))
  }

  @LuaCallback("close")
  def close(context: Context, args: Arguments): Array[Object] = {
    if (args.count == 0) {
      openPorts.clear()
      result(true)
    }
    else {
      val port = checkPort(args.checkInteger(1))
      result(openPorts.remove(port))
    }
  }

  @LuaCallback("isOpen")
  def isOpen(context: Context, args: Arguments): Array[Object] = {
    val port = checkPort(args.checkInteger(1))
    result(openPorts.contains(port))
  }

  @LuaCallback("send")
  def send(context: Context, args: Arguments): Array[Object] = {
    val address = args.checkString(1)
    val port = checkPort(args.checkInteger(2))
    node.network.sendToAddress(node, address, "network.message", Seq(Int.box(port)) ++ args.drop(2): _*)
    result(true)
  }

  @LuaCallback("broadcast")
  def broadcast(context: Context, args: Arguments): Array[Object] = {
    val port = checkPort(args.checkInteger(1))
    node.network.sendToVisible(node, "network.message", Seq(Int.box(port)) ++ args.drop(1): _*)
    result(true)
  }

  // ----------------------------------------------------------------------- //

  override def onMessage(message: Message) = {
    message.data match {
      case Array() if message.name == "computer.stopped" =>
        if (node.network.neighbors(message.source).exists(_ == this)) openPorts.clear()
      case Array(port: Integer, args@_*) if message.name == "network.message" =>
        if (openPorts.contains(port))
          node.network.sendToNeighbors(node, "computer.signal", Seq("network_message", message.source.address, port) ++ args: _*)
      case _ =>
    }
    null
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: NBTTagCompound) {
    super.load(nbt)
    if (nbt.hasKey("oc.net.openPorts")) {
      val openPortsNbt = nbt.getTagList("oc.net.openPorts")
      (0 until openPortsNbt.tagCount).
        map(openPortsNbt.tagAt).
        map(_.asInstanceOf[NBTTagInt]).
        foreach(portNbt => openPorts.add(portNbt.data))
    }
  }

  override def save(nbt: NBTTagCompound) {
    super.save(nbt)
    val openPortsNbt = new NBTTagList()
    for (port <- openPorts)
      openPortsNbt.appendTag(new NBTTagInt(null, port))
    nbt.setTag("oc.net.openPorts", openPortsNbt)
  }

  // ----------------------------------------------------------------------- //

  private def checkPort(port: Int) =
    if (port < 1 || port > 0xFFFF) throw new IllegalArgumentException("invalid port number")
    else port
}
