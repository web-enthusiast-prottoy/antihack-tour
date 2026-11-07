package bd.arena.prototype

import com.google.firebase.firestore.*
import java.time.LocalDate
import java.time.ZoneId

internal data class MatchScheduleTiming(val dateFrom:String,val dateTo:String,val fromMinute:Int,val toMinute:Int,val delayMinutes:Int) {
    fun validate() {
        val from=LocalDate.parse(dateFrom);val to=LocalDate.parse(dateTo)
        require(from<=to){"Choose a valid inclusive date range."}
        require(fromMinute in 0..1439&&toMinute in 0..1439&&toMinute>fromMinute){"Daily end time must be after the start time on the same day."}
        require(delayMinutes in 1..1440){"Enter a delay between 1 and 1440 minutes."}
    }
    fun fields():Map<String,Any> = mapOf("dateFrom" to dateFrom,"dateTo" to dateTo,"fromMinute" to fromMinute.toLong(),"toMinute" to toMinute.toLong(),"delayMinutes" to delayMinutes.toLong(),"timezone" to "Asia/Dhaka")
    fun next(earliest:Long):Long? {
        validate()
        val zone=ZoneId.of("Asia/Dhaka")
        val instant=java.time.Instant.ofEpochMilli(earliest)
        var day=maxOf(instant.atZone(zone).toLocalDate(),LocalDate.parse(dateFrom))
        if(day>LocalDate.parse(dateTo))return null
        while(day<=LocalDate.parse(dateTo)) {
            val start=day.atStartOfDay(zone).plusMinutes(fromMinute.toLong()).toInstant().toEpochMilli()
            val end=day.atStartOfDay(zone).plusMinutes(toMinute.toLong()).toInstant().toEpochMilli()
            val interval=delayMinutes*60_000L
            val offset=maxOf(0L,earliest-start)
            val slot=start+((offset+interval-1)/interval)*interval
            if(slot<=end)return slot
            day=day.plusDays(1)
        }
        return null
    }
}

internal val scheduleTemplateFields=setOf("title","description","image","mode","map","capacity","entryPaisa","prizePaisa","perKillPaisa","rules","cancellationTerms","categoryId","artworkId")
internal class MatchSchedules(private val session:AdminSession) {
    fun create(id:String,details:Map<String,Any>,prizes:List<Long>,timing:MatchScheduleTiming,artwork:ArtworkSelection,done:(Exception?)->Unit) {
        val actor=session.uid?:return
        require(session.allowed){"Staff access required."}
        timing.validate()
        require(prizes.isNotEmpty()&&prizes.size<=400&&prizes.size<=(details["capacity"] as Long)&&prizes.all{it in 0..maxPaisa}&&prizes.any{it>0}){"Add at least one positive prize position before scheduling."}
        require(prizes.sum()<=(details["prizePaisa"] as Long)){"Placement prizes must fit within the total prize."}
        val ref=session.db.document("matchSchedules/$id")
        val image=artwork.bytes?.let{session.db.document("artwork/schedule_$id")}
        val template=details.filterKeys{it in scheduleTemplateFields}+("artworkId" to (image?.id?:artwork.id))
        val fields=timing.fields()+mapOf("template" to template,"prizes" to prizes)
        session.db.runTransaction {tx->
            val old=tx.get(ref)
            val oldImage=image?.let{tx.get(it)}
            if(old.exists()) {
                require(old.getString("createdBy")==actor&&fields.all{(key,value)->old.get(key)==value}){"This schedule already exists with different details. Open Schedules to review it."}
                if(oldImage!=null)require(oldImage.getBlob("data")?.toBytes()?.contentEquals(artwork.bytes)==true){"This schedule already has different artwork. Open Schedules to review it."}
            } else {
                require(timing.next(System.currentTimeMillis())!=null){"The schedule date and time range has already ended."}
                if(image!=null&&oldImage?.exists()!=true)tx.set(image,artworkRecord(artwork.bytes,actor))
                tx.set(ref,fields+mapOf("state" to "active","revision" to 1L,"createdBy" to actor,"createdAt" to FieldValue.serverTimestamp(),"updatedBy" to actor,"updatedAt" to FieldValue.serverTimestamp()))
                tx.set(session.db.document("auditRecords/schedule_${id}_1"),mapOf("action" to "saveMatchSchedule","path" to ref.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
            }
            Unit
        }.continueWithTask{task->if(!task.isSuccessful)throw task.exception?:IllegalStateException();ref.get(Source.SERVER)}.addOnCompleteListener{task->if(session.uid==actor&&session.allowed)done(if(task.isSuccessful)null else task.exception)}
    }
    fun changeState(record:DocumentSnapshot,state:String,done:(Exception?)->Unit) {
        val actor=session.uid?:return
        if(!session.allowed)return
        session.db.runTransaction{tx->
            val old=tx.get(record.reference)
            require(old.getLong("revision")==record.getLong("revision")){"The schedule changed. Refresh before trying again."}
            require(old.getString("state") in listOf("active","paused")&&state in listOf("active","paused","stopped")){"This schedule has ended."}
            val revision=(old.getLong("revision")?:0)+1
            tx.update(record.reference,mapOf("state" to state,"revision" to revision,"updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to actor))
            tx.set(session.db.document("auditRecords/schedule_${record.id}_$revision"),mapOf("action" to "saveMatchSchedule","path" to record.reference.path,"actor" to actor,"at" to FieldValue.serverTimestamp()))
        }.addOnCompleteListener{if(session.uid==actor&&session.allowed)done(it.exception)}
    }
}
