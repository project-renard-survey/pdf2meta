package edu.umass.cs.iesl.pdf2meta.cli.extract.metatagger

import edu.umass.cs.iesl.scalacommons.Workspace
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.pdfbox.exceptions.InvalidPasswordException
import org.apache.pdfbox.pdmodel.{PDPage, PDDocument}
import collection.mutable.Buffer
import edu.umass.cs.iesl.pdf2meta.cli.extract.{MetataggerExtractor, PdfExtractorException, PdfExtractor}
import org.apache.pdfbox.pdmodel.common.{PDRectangle, PDStream}
import edu.umass.cs.iesl.pdf2meta.cli.layoutmodel._
import edu.umass.cs.iesl.pdf2meta.cli.extract.pdfbox.LayoutItemsToDocNodes
import java.awt.Dimension
import scala.xml._
import edu.umass.cs.iesl.pdf2meta.cli.coarsesegmenter.{LocalFeature, Feature, ClassifiedRectangle, ClassifiedRectangles}
import edu.umass.cs.iesl.scalacommons.collections.WeightedSet
import edu.umass.cs.iesl.pdf2meta.cli.config.StandardScoringModel
/**
 * @author <a href="mailto:dev@davidsoergel.com">David Soergel</a>
 * @version $Id$
 *  kzaporojets: adapted to metatagger
 */
class MetataggerBoxExtractor extends MetataggerExtractor with Logging with Function1[Workspace, (DocNode, ClassifiedRectangles)] {

  //TODO: read from properties file
  val mapAcceptedLabels:Map[String, String] = Map("CONTENT -> HEADERS -> TITLE" -> "HEADERS -> TITLE",
    "CONTENT -> HEADERS -> AUTHORS" -> "HEADERS -> AUTHORS",
    "CONTENT -> HEADERS -> INSTITUTION" -> "HEADERS -> INSTITUTION",
    "CONTENT -> HEADERS -> ADDRESS" -> "HEADERS -> ADDRESS",
    "CONTENT -> HEADERS -> EMAIL" -> "HEADERS -> EMAIL",
    "CONTENT -> HEADERS -> ABSTRACT" -> "HEADERS -> ABSTRACT",
    "CONTENT -> BIBLIO -> REFERENCE -> CONFERENCE" -> "REFERENCES -> CONFERENCE",
    "CONTENT -> BIBLIO -> REFERENCE -> ADDRESS" -> "REFERENCES -> ADDRESS",
    "CONTENT -> BIBLIO -> REFERENCE -> PUBLISHER" -> "REFERENCES -> PUBLISHER",
    "CONTENT -> BIBLIO -> REFERENCE -> ADDRESS" -> "REFERENCES -> ADDRESS",
    "CONTENT -> BIBLIO -> REFERENCE -> REF-MARKER" -> "REFERENCES -> REF-MARKER",
    "CONTENT -> BIBLIO -> REFERENCE -> AUTHORS" -> "REFERENCES -> AUTHORS",
    "CONTENT -> BIBLIO -> REFERENCE" -> "REFERENCES",
    "CONTENT -> BIBLIO -> REFERENCE -> TITLE" -> "REFERENCES -> TITLE",
    "CONTENT -> BIBLIO -> REFERENCE -> JOURNAL" -> "REFERENCES -> JOURNAL",
    "CONTENT -> BIBLIO -> REFERENCE -> VOLUME" -> "REFERENCES -> VOLUME",
    "CONTENT -> BIBLIO -> REFERENCE -> NUMBER" -> "REFERENCES -> NUMBER",
    "CONTENT -> BIBLIO -> REFERENCE -> PAGES" -> "REFERENCES -> PAGES",
    "CONTENT -> BIBLIO -> REFERENCE -> DATE" -> "REFERENCES -> DATE",
    "CONTENT -> BIBLIO -> REFERENCE -> BOOKTITLE" -> "REFERENCES -> BOOKTITLE",
    "CONTENT -> BIBLIO -> REFERENCE -> NOTE" -> "REFERENCES -> NOTE")

  //for recursive content such as authors that itself can be composed of firstname, lastname, etc.
  val recursiveExtraction:List[String] = List("CONTENT -> BIBLIO -> REFERENCE -> AUTHORS")

//for the text content that must be merged and not be represented by different labels (ex: sometimes the tag regarding the conference appear several times)
//however, the position of the textboxes may be different
  val mergeContent:List[String] = List("CONTENT -> BIBLIO -> REFERENCE -> CONFERENCE",
                                  "CONTENT -> BIBLIO -> REFERENCE -> DATE",
                                  "CONTENT -> BIBLIO -> REFERENCE -> ADDRESS")

  def apply(v1: Workspace) = {
    //here xml

    println ("path to xml: " + v1.file.path.toString)
    val documentMT: Elem = XML.loadFile(v1.file.path.toString)


    println ("trying to get llx from the header:")

//    processXMLRecursiveV2(documentMT \\ "content", "", "",
//        //TODO: encode the size of the page inside xml and read from there
//        new Rectangle {
//          override val bottom: Float = 0f
//          override val top: Float = 792.0f
//          override val left: Float = 0f
//          override val right: Float = 612.0f
//        })

    val docNodes:(Seq[DocNode], Seq[ClassifiedRectangle]) =
      processXMLRecursiveV2(documentMT \\ "content", "", "",
    //TODO: encode the size of the page inside xml and read from there
     new Rectangle {
        override val bottom: Float = 0f
        override val top: Float = 792.0f
        override val left: Float = 0f
        override val right: Float = 612.0f
      },true)
    println ("end of trying to get llx from the header")
    val internalDoc:InternalDocNode = new InternalDocNode("id_val", docNodes._1, None, None)
    val classifiedRectangles:ClassifiedRectangles = new ClassifiedRectangles(docNodes._2)
    (internalDoc, classifiedRectangles)

  }


  def processXMLRecursive(node:Seq[Node], parentName:String, parentId:String, pageDimensions:Rectangle):(Seq[DocNode], Seq[ClassifiedRectangle]) =
  {
    val ptrn = "([0-9].*)".r
    val seqDocNode:Seq[DocNode] = Seq()
    val seqClassifiedRectangle:Seq[ClassifiedRectangle] = Seq()


    val res = for(currentNode <- node)
      yield
      { (currentNode \ "@llx").text
        match
        {
          case ptrn(_) =>
            //println(currentNode.label + ": " + (currentNode \ "@pageNum").text)
            //(id: String,  val theRectangle: RectangleOnPage)
            if((parentName + currentNode.label).toUpperCase().contains("REFERENCE") &&
                 Math.abs((currentNode \ "@lly").text.toFloat -
                      (currentNode \ "@ury").text.toFloat)>400)
            {
              val recRes = processXMLRecursive(currentNode.child, parentName + currentNode.label + " -> ", parentId, pageDimensions)
              ((seqDocNode ++ recRes._1),
                (seqClassifiedRectangle ++ recRes._2))
            }
            else
            {
              def returnParentId(lblName:String, rect:RectangleOnPage, parentId:String)={if(lblName.toUpperCase()=="REFERENCE"){"REFERENCE_" + rect.top.toInt + "_" +
                rect.left.toInt + "_" + rect.bottom.toInt + "_" + rect.right.toInt + "_" + rect.page.pagenum +  "_"}else{parentId}}

              val rectOnPage:RectangleOnPage = new RectangleOnPage {
                override val page: Page = new Page(Integer.valueOf((currentNode \ "@pageNum").text),pageDimensions)
                override val bottom: Float = (currentNode \ "@lly").text.toFloat
                override val top: Float = (currentNode \ "@ury").text.toFloat
                override val left: Float = (currentNode \ "@llx").text.toFloat
                override val right: Float = (currentNode \ "@urx").text.toFloat
              }
              val currNode: DocNode = new DelimitingBox(
                    returnParentId(currentNode.label, rectOnPage,"") + parentId + parentName + currentNode.label
                , rectOnPage)
              val weightedFeatureSet:WeightedSet[Feature] = new WeightedSet[Feature]{
                val asMap = Map[Feature, Double]()
              }
              val weightedStringSet:WeightedSet[String] = new WeightedSet[String]{
                val asMap = Map[String, Double]()
              }


              if(mapAcceptedLabels.keys.exists(x => x==(parentName + currentNode.label).toUpperCase()))
              {
                def getContent(currentNode:scala.xml.Node, currNode:DocNode, completePath:String):String =
                                    {if(!currentNode.text.toString().contains("\n") && currNode.id.toUpperCase().contains("REFERENCE"))
                                    {
                                        ": " + currentNode.text.toString()
                                    }
                                    else if(recursiveExtraction.exists(x => x == completePath.toUpperCase()))
                                    {
                                        ": " + getRecursiveContent(currentNode)
                                    }
                                    else
                                    {
                                        ""
                                    }}
                def getRecursiveContent(currentNode:scala.xml.Node):String =
                {
                  if(currentNode.text.toString().contains("\n"))
                  {

                    currentNode.child.map(x=> getRecursiveContent(x)).mkString(" ")
                  }
                  else
                  {
                    currentNode.text
                  }
                }
                val currClassifiedRectangle: ClassifiedRectangle =
                        new ClassifiedRectangle(new MetataggerBoxTextAtom(currNode.id,
                          mapAcceptedLabels.get((parentName + currentNode.label).toUpperCase()).get + getContent(currentNode,currNode,parentName + currentNode.label), "Font", 0.0f,
                             currNode.rectangle.get, Array[Float](0f))//currNode
                  , weightedFeatureSet, weightedStringSet, None)

                val recRes = processXMLRecursive(currentNode.child, parentName + currentNode.label + " -> ",returnParentId(currentNode.label, currNode.rectangle.get,parentId),pageDimensions)

                ((seqDocNode ++ recRes._1) :+ currNode,
                  (seqClassifiedRectangle ++ recRes._2) :+ currClassifiedRectangle)
              }
              else
              {
                val recRes = processXMLRecursive(currentNode.child, parentName + currentNode.label + " -> ",returnParentId(currentNode.label, currNode.rectangle.get,parentId),pageDimensions)
                ((seqDocNode ++ recRes._1),
                  (seqClassifiedRectangle ++ recRes._2))
              }
            }
          case _ =>
            val recRes = processXMLRecursive(currentNode.child, parentName + currentNode.label + " -> ",parentId,pageDimensions)
            ((seqDocNode ++ recRes._1),
              (seqClassifiedRectangle ++ recRes._2))
        }
      }


      (res.map{t:((Seq[DocNode], Seq[ClassifiedRectangle])) => t._1}.flatten,
        res.map{t:((Seq[DocNode], Seq[ClassifiedRectangle])) => t._2}.flatten)
  }



  def processXMLRecursiveV2(node:Seq[Node], parentName:String, parentId:String, pageDimensions:Rectangle, startMerge:Boolean):(Seq[DocNode], Seq[ClassifiedRectangle]) =
  {
    val ptrn = "([0-9].*)".r
    val seqDocNode:Seq[DocNode] = Seq()
    val seqClassifiedRectangle:Seq[ClassifiedRectangle] = Seq()

    def returnParentId(lblName:String, rect:RectangleOnPage, parentId:String)={if(lblName.toUpperCase()=="REFERENCE"){"REFERENCE_" + rect.top.toInt + "_" +
      rect.left.toInt + "_" + rect.bottom.toInt + "_" + rect.right.toInt + "_" + rect.page.pagenum +  "_"}else{parentId}}

//    def invokeItself(node:Seq[Node], parentName:String, parentId:String,pageDimensions:Rectangle, startMerge:Boolean)=
//        {
//          if(node.size>1){
//            processXMLRecursiveV2(node.tail,parentName)
//          }
//        }
    val res = for(currentNode <- node.groupBy(_.label))
    yield
    { (currentNode._2(0) \ "@llx").text
    match
    {
      case ptrn(_) =>


//        val nextData =
//        if(mergeContent.exists(x => x == parentName + currentNode._1 ))
//        {
//          //merge if possible
//          if(startMerge)
//          {
//            currentNode._2(0).text.toString().contains("\n")
//          }
//        }
//        else
//        {

          val rectOnPage:RectangleOnPage = new RectangleOnPage {
            override val page: Page = new Page(Integer.valueOf((currentNode._2(0) \ "@pageNum").text),pageDimensions)
            override val bottom: Float = (currentNode._2(0) \ "@lly").text.toFloat
            override val top: Float = (currentNode._2(0) \ "@ury").text.toFloat
            override val left: Float = (currentNode._2(0) \ "@llx").text.toFloat
            override val right: Float = (currentNode._2(0) \ "@urx").text.toFloat
          }
          val currNode: DocNode = new DelimitingBox(
              returnParentId(currentNode._2(0).label, rectOnPage,"") + parentId + parentName + currentNode._2(0).label
          , rectOnPage)
          val weightedFeatureSet:WeightedSet[Feature] = new WeightedSet[Feature]{
            val asMap = Map[Feature, Double]()
          }
          val weightedStringSet:WeightedSet[String] = new WeightedSet[String]{
            val asMap = Map[String, Double]()
          }


          if(mapAcceptedLabels.keys.exists(x => x==(parentName + currentNode._2(0).label).toUpperCase()))
          {
            def getContent(currentNode:scala.xml.Node, currNode:DocNode, completePath:String):String =
            {if(!currentNode.text.toString().contains("\n") && currNode.id.toUpperCase().contains("REFERENCE"))
            {
              ": " + currentNode.text.toString()
            }
            else if(recursiveExtraction.exists(x => x == completePath.toUpperCase()))
            {
              ": " + getRecursiveContent(currentNode)
            }
            else
            {
              ""
            }}
            def getRecursiveContent(currentNode:scala.xml.Node):String =
            {
              if(currentNode.text.toString().contains("\n"))
              {

                currentNode.child.map(x=> getRecursiveContent(x)).mkString(" ")
              }
              else
              {
                currentNode.text
              }
            }
            val currClassifiedRectangle: ClassifiedRectangle =
              new ClassifiedRectangle(new MetataggerBoxTextAtom(currNode.id,
                mapAcceptedLabels.get((parentName + currentNode._2(0).label).toUpperCase()).get + getContent(currentNode._2(0),currNode,parentName + currentNode._2(0).label), "Font", 0.0f,
                currNode.rectangle.get, Array[Float](0f))//currNode
                , weightedFeatureSet, weightedStringSet, None)

            val recRes = processXMLRecursiveV2(currentNode._2(0).child, parentName + currentNode._2(0).label + " -> ",returnParentId(currentNode._2(0).label, currNode.rectangle.get,parentId),pageDimensions,true)

            val recSiblings = {if(currentNode._2.size>1){processXMLRecursiveV2(currentNode._2.tail, parentName,parentId,pageDimensions,false)}
                              else
                                {(seqDocNode,seqClassifiedRectangle)}}

            (((seqDocNode ++ recSiblings._1) ++ recRes._1) :+ currNode,
              ((seqClassifiedRectangle ++ recSiblings._2) ++ recRes._2) :+ currClassifiedRectangle)
          }
          else
          {
            val recRes:(Seq[DocNode], Seq[ClassifiedRectangle]) = processXMLRecursiveV2(currentNode._2(0).child, parentName + currentNode._2(0).label + " -> ",returnParentId(currentNode._2(0).label, currNode.rectangle.get,parentId),pageDimensions,true)
            val recSiblings:(Seq[DocNode], Seq[ClassifiedRectangle]) = {if(currentNode._2.size>1){processXMLRecursiveV2(currentNode._2.tail, parentName,parentId,pageDimensions,false)}
            else
            {(seqDocNode,seqClassifiedRectangle)}}

            (((seqDocNode ++ recSiblings._1) ++ recRes._1),
              ((seqClassifiedRectangle ++ recSiblings._2) ++ recRes._2))
          }
//        }
      case _ =>
        val recRes = processXMLRecursiveV2(currentNode._2(0).child, parentName + currentNode._2(0).label + " -> ", parentId
            /*returnParentId(currentNode._2(0).label, currNode.rectangle.get,parentId)*/ ,pageDimensions, true)
        ((seqDocNode ++ recRes._1),
          (seqClassifiedRectangle ++ recRes._2))
    }
    }

    (res.toSeq.map{t:((Seq[DocNode], Seq[ClassifiedRectangle])) => t._1}.flatten,
      res.toSeq.map{t:((Seq[DocNode], Seq[ClassifiedRectangle])) => t._2}.flatten)
  }

}

class MetataggerBoxTextAtom(override val id: String, override val theText: String,  font: String,  fontHeight: Float, val rect: RectangleOnPage,
                     val charWidths : Array[Float])
		extends TextAtom(id, theText,Some(rect))
	{
	override lazy val dominantFont : Option[FontWithHeight]             = Some(new FontWithHeight(font, fontHeight))
	override lazy val allFonts     : Seq[(FontWithHeight, Int)] = Seq((dominantFont.get, theText.length))

	override def partitionByFont(boxorder: Ordering[RectangularOnPage]) = Seq(this)


	override def create(childrenA: Seq[DocNode]) =
		{
		assert(childrenA.isEmpty)
		this
		}
	}
