import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import play.sbt.PlayImport._

/**
  * Application settings. Configure the build for your application here.
  * You normally don't have to touch the actual build definition after this.
  */
object Settings {
  /** The name of your application */
  val name = "webrtc-scalajs-play-demo"

  /** The version of your application */
  val version = "1.0.0"

  /** Options for the scala compiler */
  val scalacOptions = Seq(
    "-target:jvm-1.8",
    "-encoding", "UTF-8",
    "-unchecked",
    "-deprecation",
    "-Yno-adapted-args",
//    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
//    "-Ywarn-value-discard",
    "-Ywarn-unused",
    "-feature"
  )

  /** Declare global dependency versions here to avoid mismatches in multi part dependencies */
  object versions {
    val scala = "2.11.8"

    val scalaJsDom = "0.9.1"
    val scalaJsJQuery = "0.9.1"
    val akkaVersion = "2.4.12"
    val scalaCss = "0.5.0"
    val log4js = "1.4.10"
    val uTest = "0.4.4"
    val scalaTags = "0.6.1"
    val scalaTestPlusPlay = "1.5.1"

    val jQuery = "1.11.1"
    val bootstrap = "3.3.6"

    val scalaJsScripts = "1.0.0"
    val udash = "0.4.0"
    val uPickle = "0.4.3"
    val fontAwesome = "4.7.0"
  }

  /**
    * These dependencies are shared between JS and JVM projects
    * the special %%% function selects the correct version for each project
    */
  val sharedDependencies = Def.setting(Seq(
    "com.lihaoyi" %%% "upickle" % versions.uPickle,
    "io.udash" %%% "udash-core-shared" % versions.udash
  ))

  /** Dependencies only used by the JVM project */
  val jvmDependencies = Def.setting(Seq(
    ws,
    "com.vmunier" %% "scalajs-scripts" % versions.scalaJsScripts,
    "org.webjars" % "font-awesome" % versions.fontAwesome % Provided,
    "org.webjars" % "bootstrap" % versions.bootstrap % Provided,
    "com.lihaoyi" %% "utest" % versions.uTest % Test,
    "com.typesafe.akka" %% "akka-testkit" % versions.akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % versions.akkaVersion % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % versions.scalaTestPlusPlay % Test
  ))

  /** Dependencies only used by the JS project (note the use of %%% instead of %%) */
  val scalajsDependencies = Def.setting(Seq(
    "io.udash" %%% "udash-core-frontend" % versions.udash,
    "be.doeraene" %%% "scalajs-jquery" % versions.scalaJsJQuery,
    "org.scala-js" %%% "scalajs-dom" % versions.scalaJsDom,
    "com.lihaoyi" %%% "scalatags" % versions.scalaTags,
    "com.lihaoyi" %%% "utest" % versions.uTest % Test
  ))

  /** Dependencies for external JS libs that are bundled into a single .js file according to dependency order */
  val jsDependencies = Def.setting(Seq(
    "org.webjars" % "jquery" % versions.jQuery / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "bootstrap" % versions.bootstrap / "bootstrap.js" minified "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars" % "log4javascript" % versions.log4js / "js/log4javascript_uncompressed.js" minified "js/log4javascript.js"
  ))
}
