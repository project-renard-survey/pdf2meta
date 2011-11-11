package edu.umass.cs.iesl.pdf2meta.cli.layoutmodel

import collection.Seq
import edu.umass.cs.iesl.pdf2meta.cli.util.Util

//class TextBox(id: String, children: Seq[DocNode]) extends DocNode(id, children) with HasFontInfo
trait TextBox extends HasFontInfo
  {
  self: DocNode =>

  override def toString = text.substring(text.length.max(30).min(0))

  def spanText =
    {
    if (text.length < 80)
      {text}
    else
      {
      text.substring(0,37) + " ... " + text.substring(text.length - 37, text.length)
      }
    }

  // todo make lazy
  def mkString(d: String): String =
    {
    (for (x <- children.collect
               {case x: HasFontInfo => x}) yield
      {x.text}).mkString(d)
    }

  lazy val allFonts: Seq[(FontWithHeight, Int)] =
    {
    if (children.isEmpty)
      {
      // Must be a RectBox or something, with no fonts in it
      Seq()
      }
    else
      {
      val leafFonts = for (leaf <- allLeaves if leaf.dominantFont.isDefined) yield (leaf.dominantFont.get, leaf.text.size)
      Util.histogramAccumulate(leafFonts).toSeq
      }
    }

  lazy val dominantFont: Option[FontWithHeight] =
    {
    allFonts.length match
    {
      case 0 => None
      case _ =>
        {
        val bestFont = allFonts.toSeq.sortWith((a, b) => a._2 > b._2).head
        if (bestFont._2 > text.length() * .5)
          {
          Some(bestFont._1)
          }
        else None
        }
    }
    }

  lazy val allTextLineWidths: Seq[(Double, Int)] =
    {
    if (children.isEmpty)
      {
      // Must be a RectBox or something, with no fonts in it
      Seq()
      }
    else
      {
      // round up to the nearest 10; this is just a rough estimate of column width
      val lineWidthsWithLength = textLines.map(t => (t.rectangle.map(r => ((r.width / 10.0).ceil * 10.0)).getOrElse(0.0), t.text.length))
      Util.histogramAccumulate(lineWidthsWithLength).toSeq
      }
    }

  lazy val dominantTextLineWidth: Option[Double] =
    {
    textLines.length match
    {
      case 0 => None
      case _ =>
        {
        val sortedTextLineWidths: Seq[(Double, Int)] = allTextLineWidths.toSeq.sortWith((a, b) => a._2 > b._2)
        if (sortedTextLineWidths.isEmpty)
          {
          None
          }
        else
          {
          val bestLineWidth = sortedTextLineWidths.head
          if (bestLineWidth._2 > text.length() * .5)
            {
            Some(bestLineWidth._1)
            }
          else None
          }
        }
    }
    }


  // first put all the font blocks from the children on equal footing
  def partitionByFont(boxorder: Ordering[RectangularOnPage]): Seq[DocNode] = // children.map(_.splitByFont).flatten
    {
    // ignore any child that isn't a TextBox.
    // it never happens otherwise anyway, and if it did, the layout partitioning should cover that case
    //val c: Seq[DocNode] = children.collect({case x: TextBox => x})
    val s: Seq[DocNode] = children.map(_.partitionByFont(boxorder)).flatten
    val fontSplitChildren: Seq[DocNode] = s.map(_.children).flatten
    //.collect({case x: TextBox => x}) // when TextBoxes were collections, this was s.flatten.collect
    // then order them
    val sortedChildren = fontSplitChildren.sorted(boxorder)

    // then regroup them
    // merge if the blocks sort adjacently AND are in the same column
    // we don't want blocks in two columns to merge because the encompassing box will be interpreted wrongly wrt
    // ordering one level up
    type FontAndLeft = (FontWithHeight, Double)
    val fontBlocks: List[(((String, Double), List[DocNode]), Int)] =
      {
      val fontAndLeftMatch: (DocNode => (String, Double)) = ((x: DocNode) =>
        {
        (x.dominantFont.get.fontid, x.rectangle.get.left) // ignore the font height
        })
      // HasFontInfo
      val runs: List[((String, Double), List[DocNode])] = Util.contiguousRuns(sortedChildren.toList)(fontAndLeftMatch)
      runs.zipWithIndex
      };

    fontBlocks.map({
                   case ((key, blocks), index) => DocNode(id + "." + index, blocks, None, None, true, true)
                   })

    // the regrouped boxes are still in order, but now only TextBoxes are provided
    }
  }

















