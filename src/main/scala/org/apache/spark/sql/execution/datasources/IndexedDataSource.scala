/*
 * Copyright 2016 Lightcopy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources

import scala.util.{Try, Success, Failure}

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileStatus, Path}

import org.apache.spark.internal.Logging
import org.apache.spark.sql.{Column, SaveMode, SparkSession}
import org.apache.spark.sql.execution.datasources.parquet.ParquetIndexFileFormat
import org.apache.spark.sql.sources.BaseRelation
import org.apache.spark.util.Utils

/** DataSource to resolve relations that support indexing */
case class IndexedDataSource(
    sparkSession: SparkSession,
    className: String,
    mode: SaveMode = SaveMode.ErrorIfExists,
    options: Map[String, String] = Map.empty) extends Logging {

  lazy val providingClass: Class[_] = IndexedDataSource.lookupDataSource(className)
  lazy val tablePath: FileStatus = {
    val path = options.getOrElse("path", sys.error("path option is required"))
    IndexedDataSource.resolveTablePath(new Path(path),
      sparkSession.sparkContext.hadoopConfiguration)
  }

  def resolveRelation(): BaseRelation = {
    null
  }

  def createIndex(columns: Seq[Column]): Unit = {

  }

  def deleteIndex(): Unit = {

  }
}

object IndexedDataSource {
  val parquet = classOf[ParquetIndexFileFormat].getCanonicalName

  /**
   * Resolve class name into fully-qualified class path if available. If no match found, return
   * itself. [[IndexedDataSource]] checks whether or not class is a valid indexed source.
   */
  def resolveClassName(provider: String): String = provider match {
    case "parquet" => parquet
    case "org.apache.spark.sql.execution.datasources.parquet" => parquet
    case other => other
  }

  /** Simplified version of looking up datasource class */
  def lookupDataSource(provider: String): Class[_] = {
    val provider1 = IndexedDataSource.resolveClassName(provider)
    val provider2 = s"$provider.DefaultSource"
    val loader = Utils.getContextOrSparkClassLoader
    Try(loader.loadClass(provider1)).orElse(Try(loader.loadClass(provider1))) match {
      case Success(dataSource) =>
        dataSource
      case Failure(error) =>
        throw new ClassNotFoundException(
          s"Failed to find data source: $provider", error)
    }
  }

  /** Resolve table path into file status, should not contain any glob expansions */
  def resolveTablePath(path: Path, conf: Configuration): FileStatus = {
    val fs = path.getFileSystem(conf)
    fs.getFileStatus(path)
  }
}
