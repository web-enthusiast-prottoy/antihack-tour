package bd.arena.prototype

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun MatchScheduleField(value:String,enabled:Boolean,label:String="Starts · Bangladesh time",change:(String)->Unit) {
    val context=LocalContext.current
    val selected=runCatching{LocalDateTime.parse(value.replace(' ','T'))}.getOrElse{LocalDateTime.now().plusDays(1)}
    OutlinedButton(onClick={
        DatePickerDialog(context,{_,year,month,day->
            TimePickerDialog(context,{_,hour,minute->change(LocalDateTime.of(year,month+1,day,hour,minute).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))},selected.hour,selected.minute,false).show()
        },selected.year,selected.monthValue-1,selected.dayOfMonth).show()
    },enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Text(label+": "+if(value.isBlank())"Choose date & time" else selected.format(DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a")))
    }
}
