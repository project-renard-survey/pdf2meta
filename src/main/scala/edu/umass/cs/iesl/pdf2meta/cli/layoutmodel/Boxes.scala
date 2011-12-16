package edu.umass.cs.iesl.pdf2meta.cli.layoutmodel

import collection.Seq

trait TextContainer
  {
  final def text: String = mkString(" ")
  def mkString(d: String): String
  }

class FontWithHeight(val fontid: String, rawheight: Double)
  {
  val height = (rawheight * 10.0).round / 10.0
  override def equals(p1: Any) =
    {
    p1 match
    {
      case x: FontWithHeight => (fontid == x.fontid && height == x.height)
      case _ => false
    }
    }

  override def hashCode: Int = 41 * (41 + fontid.hashCode) + height.hashCode()

  def equalsWithinOneQuantum(p1: FontWithHeight): Boolean =
    {
    p1 match
    {
      case x: FontWithHeight => (fontid == x.fontid && (height - x.height).abs <= 0.1)
      case _ => false
    }
    }


  def sizeEqualsWithinOneQuantum(p1: FontWithHeight): Boolean = sizeEqualsWithin(0.1)(p1)

  def sizeEqualsWithin(epsilon: Double)(p1: FontWithHeight): Boolean =
    {
    p1 match
    {
      case x: FontWithHeight => ((height - x.height).abs <= epsilon)
      case _ => false
    }
    }
  override def toString = fontid + " " + height
  }

trait HasFontInfo extends TextContainer
  {
  def dominantFont: Option[FontWithHeight]
  }


class DelimitingBox(id: String, val theRectangle: RectangleOnPage)
        extends DocNode(id, Seq.empty, None, None)
  {
  override def computeRectangle = Some(theRectangle)
  override def create(childrenA: Seq[DocNode]) =
    {
    assert(childrenA.isEmpty)
    this
    }
  }

class RectBox(id: String, override val theRectangle: RectangleOnPage)
        extends DelimitingBox(id, theRectangle)

class CurveBox(id: String, override val theRectangle: RectangleOnPage)
        extends DelimitingBox(id, theRectangle)

class FigureBox(id: String, override val theRectangle: RectangleOnPage)
        extends DelimitingBox(id, theRectangle)


