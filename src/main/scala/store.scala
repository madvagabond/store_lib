package store 
import scodec._
import scodec.bits._, codecs._

import com.twitter.util.{Try, Return}
import org.iq80.leveldb._


import org.fusesource.leveldbjni.JniDBFactory._

//add write ahead cache

object util {
  import com.twitter.io.Buf

  def optAsTry[T](opt: Option[T] ): Try[T] = Try {
    opt match {
      case Some(x) => x
      case None => throw ( new Exception("Null Data") )
    }
  }

   def configure(block_size: Int = 512, num_entries: Int = 1000): Options = {
     val opt = new Options()
     opt.cacheSize(block_size * num_entries)
     opt.blockSize(num_entries)
     opt.createIfMissing(true)
     opt
   }


  def asBuf(b: Array[Byte])  = Buf.ByteArray.Shared(b)
 

  object BV {
    def fromBuf(buf: Buf) = ( Buf.ByteArray.Shared.extract(buf) ) andThen (b => BitVector(b) ) 
  }


}

object BlockStore {

  import java.nio.file.{Files => FileModule, Paths}
  import java.io.File, com.google.common.io.Files

  case class Bucket(path: String, db: DB, opts: Options )


  object Bucket { 

    def make(path: String, opt: Options): Try[Bucket] = Try {
      val fp = new File(path)
      if (opt.createIfMissing() != true ) opt.createIfMissing(true)  
      val db = factory.open(fp, opt)
      Bucket( path, db , opt)
    }

    def destroy(bckt: Bucket): Try[Unit] = Try {  bckt.db.close(); factory.destroy(new File(bckt.path), bckt.opts ) } 
    

    def get(b: Bucket, key: Array[Byte]) = Try { b.db.get(key) }.toOption

    def put(b: Bucket, key: Array[Byte], value: Array[Byte] ) = Try { b.db.put(key, value) }

    def delete(b: Bucket, key: Array[Byte]) = Try { b.db.delete(key) }

  }


}


object KV {

  import scala.collection.concurrent.TrieMap
  import BlockStore.Bucket

  type BucketMap = TrieMap[String, Bucket]


  object BucketMap {
    //destroy

    def make = new BucketMap()

    def mkBucket(bckts: BucketMap, p: String) = {
      val opts = util.configure()
      val b = Bucket.make(p, opts)
      b.flatMap(bckt => Try { bckts += (p -> bckt) }  ) 
    }

    def rmBucket(bckts: BucketMap, p: String): Try[Unit] = Try {

      bckts.get(p) match {
        case Some(b) =>
          Bucket.destroy(b)
          bckts -= p 

        case None => () 
      }


    }

    def getBucket(bckts: BucketMap, p: String): Option[Bucket] = { bckts.get(p) }

  }
}