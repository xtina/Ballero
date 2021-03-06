package com.cldellow.ballero.ui

import com.cldellow.ballero.R
import com.cldellow.ballero.data._
import android.graphics._
import java.util.concurrent.atomic._
import scala.collection.JavaConversions._
import android.app.Activity
import android.content._
import android.location._
import android.os.Bundle
import android.util.Log
import android.view._
import android.widget.TextView
import greendroid.app._
import greendroid.widget._
import greendroid.widget.item._

class QueuedProjectDetailsActivity extends ProjectishActivity {
  val TAG = "QueuedProjectDetailsActivity"
  var pending = 0
  var policy: RefreshPolicy = FetchIfNeeded
  val isProject = false
  var patternId: Option[Int] = None

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    policy = FetchIfNeeded
    setTitle("queued project details")
    btnTakePhoto.setVisibility(View.GONE)
  }


  override def onResume() {
    super.onResume()

    lblMadeFor.setText("make for")
    lblCompletedOn.setText("finish by")
    refreshAll(FetchIfNeeded)
  }

  def doFetch(localPolicy: RefreshPolicy) {
    policy = localPolicy

    pending += 2
    RavelryApi.makeQueueDetailsResource(currentId).render(policy, onQueueDetails(new AtomicInteger(2)))
  }


  private def onQueueDetails(sanity: AtomicInteger)(ravelryQueue: List[RavelryQueue], delta: Int) {
    pending += delta
    val rv = sanity.addAndGet(delta)
    if(rv>0)
      return

    ravelryQueue map { q =>
      patternId = q.pattern_id
      q.pattern_id.map { id =>
        pending += 2
        RavelryApi.makePatternDetailsResource(id).render(policy, onPatternDetails(new AtomicInteger(2), q))
      }

      if(q.pattern_id.isEmpty) {
        pending += 2
        Data.currentUser.get.queue.render(policy, onSimpleQueue(new AtomicInteger(2), q, None))
      }
    }
  }

  private def onPatternDetails(sanity: AtomicInteger, q: RavelryQueue)(patternDetails: List[Pattern], delta: Int) {
    pending += delta
    val rv = sanity.addAndGet(delta)
    if(rv > 0)
      return

    patternDetails map { patternDetails =>
      pending += 2
      Data.currentUser.get.queue.render(policy, onSimpleQueue(new AtomicInteger(2), q, Some(patternDetails)))
    }
  }

  private def onSimpleQueue(sanity: AtomicInteger, q: RavelryQueue, pattern: Option[Pattern])(queue: List[SimpleQueuedProject], delta: Int) {
    pending += delta
    val rv = sanity.addAndGet(delta)
    if(rv > 0)
      return

    if(pending <= 0) {
      pending = 0
      dismissProgressDialog()
    }

    if(queue.isEmpty)
      return
    val sq = queue.filter { _.id == currentId }.head

    myYarnLayout.setVisibility(View.GONE)
    if(!q.queued_stashes.map { _.isEmpty }.getOrElse(true)) {
    } else if(q.skeins.isDefined || (q.yarn_name.isDefined && q.yarn_name.get.trim != "")) {
      val useYarnsAdapter = new ItemAdapter(this)
      useYarnsAdapter.add(
        new SubtitleItem2(
          q.yarn_name.getOrElse("unknown yarn"),
          q.skeins.map { x => "%s skeins".format(x) }.getOrElse(""),
        ""))

      listViewMyYarn.setAdapter(useYarnsAdapter)
      myYarnLayout.setVisibility(View.VISIBLE)
    }

    patternName.setVisibility(View.VISIBLE)
    q.pattern_name.foreach { name =>
      patternName.setText(name)
    }

    pattern.foreach { pattern =>
      patternName.setText(pattern.name)
    }

    val imageUrls = pattern.flatMap { _.photos }.map { photos =>
      photos.map { photo =>
        photo.square_url.getOrElse(photo.thumbnail_url)
      }
    }.getOrElse(Nil).take(3)

    if(imageUrls.length > 0) {
      val imageAdapter = new AsyncImageViewAdapter(this, imageUrls.toArray)
      gallery.setAdapter(imageAdapter)
      gallery.setSelection(imageUrls.length / 2)
      gallery.setVisibility(View.VISIBLE)
    } else {
      gallery.setVisibility(View.GONE)
    }

    lblHappiness.setVisibility(View.GONE)
    imageViewHappiness.setVisibility(View.GONE)

    progressBar.setVisibility(View.GONE)
    status.setVisibility(View.GONE)

    var notes = sq.notes.getOrElse("")
    if(notes.trim=="")
      notesValue.setText("(no notes)")
    else
      notesValue.setText(notes)

    var madeFor = q.make_for.getOrElse("")
    if(madeFor.trim == "")
      madeFor = "unknown"

    madeForValue.setText(madeFor)

    yarnLayout.setVisibility(View.GONE)

    var yarnRequirementsVis = View.GONE
    lblYarnYardage.setVisibility(View.GONE)
    lblYarnSize.setVisibility(View.GONE)
    pattern.foreach { _.yardage_description.foreach { d =>
      if(!("yards".equals(d.trim))) {
        yarnRequirementsVis = View.VISIBLE
        lblYarnYardage.setVisibility(View.VISIBLE)
        lblYarnYardage.setText(d)
      }
    } }
    pattern.foreach { _.yarn_weight_description.foreach { d =>
      yarnRequirementsVis = View.VISIBLE
      lblYarnSize.setVisibility(View.VISIBLE)
      lblYarnSize.setText(d)
    } }
    yarnRequirementsLayout.setVisibility(yarnRequirementsVis)
    lblYarns.setText("suggested yarns")
    val adapter: ItemAdapter = new ItemAdapter(this)
    pattern.foreach { _.packs.foreach { packs =>
      packs.filter { pk => pk.yarn.isDefined && pk.yarn.get.name.isDefined }.foreach { pack =>
        yarnLayout.setVisibility(View.VISIBLE)
        val title = pack.yarn.flatMap { _.yarn_company_name }.getOrElse("Unknown brand")
        val subtitle = pack.yarn.flatMap { _.name }.getOrElse("Unknown yarn")
        val subtitle2 = List[Option[String]](
          pack.colorway, pack.skeins.map { x => "%s skeins".format(if(x == x.toInt) x.toInt else x) },
          pack.total_grams.map { x => "%s g".format(x) },
          pack.total_yards.map { x => "%s yards".format(x.toInt) }).flatten.filter { _ != "" }.mkString(", ")

        adapter.add(new SubtitleItem2(title, subtitle, subtitle2))
      }
    } }
    listViewYarn.setAdapter(adapter)

    lblCompletedOnValue.setText("unknown")
    q.finish_by.map { c => 
      // This contains a time and TZ offset. WTF.
      val actual =
        if(c.contains(" "))
          c.substring(0, c.indexOf(" "))
        else
          c
      lblCompletedOnValue.setText(actual)
    }

    lblStartedOn.setVisibility(View.GONE)
    lblStartedOnValue.setVisibility(View.GONE)

    lblStartedOn.setText("queued on")
    lblStartedOnValue.setText("unknown")
    q.created_at.map { c => 
      lblStartedOn.setVisibility(View.VISIBLE)
      lblStartedOnValue.setVisibility(View.VISIBLE)
      // This contains a time and TZ offset. WTF.
      val actual =
        if(c.contains(" "))
          c.substring(0, c.indexOf(" "))
        else
          c
      lblStartedOnValue.setText(actual)
    }

    needleLayout.setVisibility(View.GONE)
    pattern.foreach { _.pattern_needle_sizes.foreach { needle_sizes =>
      val needles = needle_sizes.map { needle =>
        needle.name
      }.flatten.mkString("\n")
      if(needles != "") {
        needleLayout.setVisibility(View.VISIBLE)
        needleDetails.setText(needles)
      }
    } }

    tagsLayout.setVisibility(View.GONE)
    /** TODO: tags 
      Looks like API doesn't expose these yet.
    */

    /*
    tagsContentLayout.removeAllViews()
    project.tag_names.foreach { tag_names =>
      tag_names.foreach { tag_name =>
        tagsLayout.setVisibility(View.VISIBLE)
        val textView = new TextView(this)
        textView.setVisibility(View.VISIBLE)
        textView.setText(tag_name)
        textView.setBackgroundColor(Color.LTGRAY)
        textView.setPadding(10,2,10,2)
        textView.setTextColor(Color.BLACK)
        textView.setSingleLine(true)
        tagsContentLayout.addView(textView, new PredicateLayout.LayoutParams(10,4))
      }
    }
    */
    linearLayout.setVisibility(View.VISIBLE)

  }
}
