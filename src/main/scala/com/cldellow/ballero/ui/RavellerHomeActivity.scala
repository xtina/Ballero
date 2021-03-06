package com.cldellow.ballero.ui

import com.cldellow.ballero.R
import com.cldellow.ballero.service._
import com.cldellow.ballero.data._
import scala.xml.XML

import scala.collection.JavaConversions._
import android.app.Activity
import android.content._
import android.location._
import android.os.Bundle
import android.util.Log
import android.view._
import android.widget._
import greendroid.app._
import greendroid.widget.ActionBarItem.Type
import greendroid.graphics.drawable._
import greendroid.widget._
import greendroid.widget.item._

class RavellerHomeActivity extends GDListActivity with NavigableListActivity with SmartActivity {
  val TAG = "RavellerHomeActivity"

  var adapter: ItemAdapter = null

  var needlesItem: SubtitleItem = null
  var projectsItem: SubtitleItem = null
  var stashedItem: SubtitleItem = null

  var numPending: Int = 0

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    getParams[User] map { user => Data.currentUser = Some(user) }
    setTitle(Data.currentUser.get.name)
    adapter = new ItemAdapter(this)

    needlesItem = new SubtitleItem("needles", "", true).goesTo[NeedlesActivity]
    projectsItem = new SubtitleItem("projects", "", true).goesTo[ProjectsActivity]
    stashedItem = new SubtitleItem("stashed", "", true).goesTo[StashListActivity]
    adapter.add(projectsItem)
    adapter.add(stashedItem)
    adapter.add(needlesItem)

    setListAdapter(adapter)
  }

  override def onResume {
    super.onResume
    refreshAll(FetchIfNeeded)
  }

  private var projectsPending = 0
  private var needlesPending = 0
  private var stashPending = 0
  private def refreshAll(policy: RefreshPolicy) {
    numPending += 8
    projectsPending += 4
    needlesPending += 2
    stashPending += 2
    Data.currentUser.get.stash.render(policy, onStashChanged)
    Data.currentUser.get.needles.render(policy, onNeedlesChanged)
    Data.currentUser.get.queue.render(policy, onQueueChanged)
    Data.currentUser.get.projects.render(policy, onProjectsChanged)
  }

  private def updatePendings(delta: Int) {
    numPending += delta
    if(numPending <= 0) {
      numPending = 0
    }
  }

  private var queued: List[SimpleQueuedProject] = Nil
  private var projects: List[Project] = Nil

  private def onQueueChanged(queued: List[SimpleQueuedProject], delta: Int) {
    updatePendings(delta)
    projectsPending += delta

    this.queued = queued
    updateProjectsItem

  }

  private def onProjectsChanged(projects: List[Project], delta: Int) {
    updatePendings(delta)

    projectsPending += delta

    this.projects = projects
    updateProjectsItem
  }

  private def updateProjectsItem {
    val subtitle = 
      if(queued.isEmpty && projects.isEmpty)
        "no projects"
      else {
      val queue = if(queued.isEmpty) "queue empty" else "%s queued".format(queued.length)
      val projectStrings = projects.filter { proj => proj.status == InProgress || proj.status == Finished }.groupBy {
        _.status_name.getOrElse("In progress") }.map {
      case (status, items) => "%s %s".format(items.length, status.toLowerCase) }

      (projectStrings.toList ::: List(queue)).mkString(", ")
    }

    projectsItem.subtitle = subtitle
    projectsItem.inProgress = projectsPending > 0

    onContentChanged
  }

  private def onStashChanged(stash: List[SentinelStashedYarn], delta: Int) {
    updatePendings(delta)
    stashPending += delta

    val subtitle = if(stash.isEmpty) "no stashed yarn" else "%s yarns stashed".format(stash.length)

    stashedItem.subtitle = subtitle
    stashedItem.inProgress = stashPending > 0

    onContentChanged
  }


  private def onNeedlesChanged(needles: List[Needle], delta: Int) {
    updatePendings(delta)
    needlesPending += delta

    val subtitle = if(needles.isEmpty) "no needles" else needles.groupBy { _.kind }
      .map { case(kind, needles) => "%s %s".format(needles.length, kind) }
      .mkString(", ")

    needlesItem.subtitle = subtitle
    needlesItem.inProgress = needlesPending > 0

    onContentChanged
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater: MenuInflater = getMenuInflater()
    inflater.inflate(R.menu.raveller_home_menu, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.refresh => refreshAll(ForceNetwork)
      case _ =>
    }
    true
  }
}
