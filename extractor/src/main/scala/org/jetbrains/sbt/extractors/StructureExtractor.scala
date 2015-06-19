package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.Utilities._
import sbt._

//import scala.language.reflectiveCalls

object StructureExtractor extends Extractor {

  override type Data = StructureData

  def extract(implicit state: State, options: Extractor.Options): Option[Data] = {
    val acceptedProjectRefs =
      // Here is a hackish way to test whether project has JvmPlugin enabled.
      // Prior to 0.13.8 SBT had this one enabled by default for all projects.
      // Now there may exist projects with IvyPlugin (and thus JvmPlugin) disabled
      // lacking all the settings we need to extract in order to import project in IDEA.
      // These projects are filtered out by checking `autoPlugins` field.
      // But earlier versions of SBT 0.13.x had no `autoPlugins` field so
      // structural typing is used to get the data.
      structure.allProjectRefs.filter { case ProjectRef(_, id) =>
        structure.allProjects.find(_.id == id).map { resolvedProject =>
          try {
            type ResolvedProject_0_13_7 = {def autoPlugins: Seq[{ def label: String}]}
            val resolvedProject_0_13_7 = resolvedProject.asInstanceOf[ResolvedProject_0_13_7]
            val labels = resolvedProject_0_13_7.autoPlugins.map(_.label)
            labels.contains("sbt.plugins.JvmPlugin")
          } catch {
            case _ : NoSuchMethodException => true
          }
        }.getOrElse(false)
      }

    val sbtVersion      = setting(Keys.sbtVersion).get
    val projectsData    = acceptedProjectRefs.flatMap(new ProjectExtractor(_).extract)
    val repositoryData  = if (options.download) new RepositoryExtractor(acceptedProjectRefs).extract else None
    val localCachePath  = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
    Some(StructureData(sbtVersion, projectsData, repositoryData, localCachePath))
  }
}