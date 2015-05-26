package org.kframework.tiny

import org.kframework.attributes._
import org.kframework.builtin.Sorts
import org.kframework.kore.{Constructors => basic, _}
import org.kframework.meta.{Down, Up}
import org.kframework.tiny.builtin.{BagLabel, KMapAppLabel, MapKeys, Tuple2Label}
import org.kframework.{definition, kore, tiny}

import scala.collection.JavaConverters._

class Constructors(val module: definition.Module) extends kore.Constructors[K] with ScalaSugar[K] {

  implicit val theory = new TheoryWithUpDown(new Up(this, Set()), new Down(Set()), module)

  // separate the hook mappings at some point
  def hookMappings(hook: String, labelString: String) = hook match {
    case "#K-EQUAL:_==K_" => Equals
    case "#BOOL:notBool_" => Not //NativeUnaryOpLabel("notBool_", Att(), (x: Boolean) => !x, Sorts.Bool)
    case "#INT:_+Int_" => NativeBinaryOpLabel(labelString, Att(), (x: Int, y: Int) => x + y, Sorts.Int)
    case "#INT:_-Int_" => NativeBinaryOpLabel(labelString, Att(), (x: Int, y: Int) => x - y, Sorts.Int)
    case "#INT:_*Int_" => NativeBinaryOpLabel(labelString, Att(), (x: Int, y: Int) => x * y, Sorts.Int)
    case "#INT:_/Int_" => NativeBinaryOpLabel(labelString, Att(), (x: Int, y: Int) => x / y, Sorts.Int)
    case "#INT:_<=Int_" => NativeBinaryOpLabel(labelString, Att(), (x: Int, y: Int) => x <= y, Sorts.Bool)
    case "Map:.Map" => KMapAppLabel(labelString)
    case "Map:__" => KMapAppLabel(labelString)
    case "Map:_|->_" => Tuple2Label
    case "Map:keys" => MapKeys
    case "Set:in" => RegularKAppLabel("???in???", Att())
    case "#BOOL:_andBool_" => And //NativeBinaryOpLabel("_andBool_", Att(), (x: Boolean, y: Boolean) => x && y, Sorts
    // .Bool)
    case "#BOOL:_orBool_" => Or //NativeBinaryOpLabel("_orBool_", Att(), (x: Boolean, y: Boolean) => x || y, Sorts.Int)
  }

  val uniqueLabelCache = collection.mutable.Map[String, Label]()

  override def KLabel(name: String): Label = {

    val res = if (name.startsWith("'<")) {
      RegularKAppLabel(name, Att())
    } else if (name.startsWith("is")) {
      SortPredicateLabel(Sort(name.replace("is", "")))
    } else {
      val att = module.attributesFor(KORE.KLabel(name))
      if (att.contains("assoc"))
        if (att.contains("comm"))
          BagLabel(name, att)
        else
          RegularKAssocAppLabel(name, att)
      else
        att.get[String]("hook").map(hookMappings(_, name)).getOrElse { RegularKAppLabel(name, att) }
    }

    uniqueLabelCache.getOrElseUpdate(res.name, res)
  }

  override def KApply(klabel: kore.KLabel, klist: kore.KList, att: Att): K = {
    val x: Label = convert(klabel)
    x(klist.items.asScala.toSeq map convert, att)
  }

  def KApply(klabel: kore.KLabel, list: List[tiny.K], att: Att): KApp = {
    val x: Label = convert(klabel)
    x(list, att).asInstanceOf[KApp]
  }

  def KApply(klabel: kore.KLabel, list: List[tiny.K]): KApp = KApply(klabel, list, Att())

  override def KSequence[KK <: kore.K](items: java.util.List[KK], att: Att): K =
    KSeq(items.asScala.toSeq map convert, att)

  override def KVariable(name: String, att: Att): KVar = KVar(name, att)

  override def Sort(name: String): kore.Sort = KORE.Sort(name)

  override def KToken(s: String, sort: Sort, att: Att): K = {
    sort match {
      case Sorts.KString => TypedKTok(sort, s)
      case Sorts.Int => TypedKTok(sort, s.toInt)
      case Sorts.KBool => s match {
        case "KTrue" => And()
        case "KFalse" => Or()
      }
      case _ => RegularKTok(sort, s)
    }
  }


  override def KRewrite(left: kore.K, right: kore.K, att: Att) = tiny.KRewrite(convert(left), convert
    (right), att)

  override def KList[KK <: kore.K](items: java.util.List[KK]): kore.KList = KORE.KList(items)

  override def InjectedKLabel(klabel: kore.KLabel, att: Att): InjectedKLabel = InjectedLabel(convert(klabel), att)

  def convert(l: kore.KLabel): Label = l match {
    case l: Label => l
    case Unapply.KLabel(name) => KLabel(name)
  }
  def convert(k: kore.K): tiny.K = k match {
    case k: K => k
    case t@Unapply.KVariable(name) => KVariable(name, t.att)
    case t@Unapply.KToken(v, s) => KToken(v, s, t.att)
    case t@Unapply.KRewrite(left, right) => KRewrite(convert(left), convert(right), t.att)
    case t@Unapply.KSequence(s) => KSequence((s map convert).asJava, t.att)
    case t@Unapply.KApply(label, list) => KApply(label, KList((list map convert).asJava), t.att)
  }

  @annotation.varargs def Att(ks: K*) = org.kframework.attributes.Att(ks: _*)

  implicit class KVarWithArrow(k: KVar) {
    def ->(other: K) = Binding(k, other)
  }

  implicit def Tuple2IsBinding(t: (K, K)) = Binding(t._1, t._2)
}
