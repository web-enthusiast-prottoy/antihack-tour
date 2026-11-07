export const scheduleZone='Asia/Dhaka';
const formatter=new Intl.DateTimeFormat('en-CA',{timeZone:scheduleZone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'});
const parts=ms=>Object.fromEntries(formatter.formatToParts(new Date(ms)).filter(p=>p.type!=='literal').map(p=>[p.type,p.value]));
export function validDate(day) {
 if(typeof day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(day))return false;
 const value=new Date(day+'T00:00:00Z');return Number.isFinite(value.getTime())&&value.toISOString().slice(0,10)===day;
}
export function localInstant(day,minute) {
 const [year,month,date]=day.split('-').map(Number),desired=Date.UTC(year,month-1,date,Math.floor(minute/60),minute%60);
 let instant=desired;
 for(let i=0;i<3;i++) {const p=parts(instant);instant+=desired-Date.UTC(+p.year,+p.month-1,+p.day,+p.hour,+p.minute,+p.second);}
 return instant;
}
export function validateTiming(s) {
 if(s.timezone!==scheduleZone||!validDate(s.dateFrom)||!validDate(s.dateTo)||s.dateFrom>s.dateTo)throw Error('Choose a valid inclusive date range.');
 if(!Number.isInteger(s.fromMinute)||!Number.isInteger(s.toMinute)||s.fromMinute<0||s.toMinute>1439||s.toMinute<=s.fromMinute)throw Error('Daily end time must be after the start time on the same day.');
 if(!Number.isInteger(s.delayMinutes)||s.delayMinutes<1||s.delayMinutes>1440)throw Error('Enter a delay between 1 and 1440 minutes.');
}
export function nextWindowTime(s,earliest) {
 validateTiming(s);
 const p=parts(earliest);let day=[p.year,p.month,p.day].join('-');
 if(day<s.dateFrom)day=s.dateFrom;
 if(day>s.dateTo)return null;
 while(day<=s.dateTo) {
  const start=localInstant(day,s.fromMinute),end=localInstant(day,s.toMinute),interval=s.delayMinutes*60000;
  const slot=start+Math.ceil(Math.max(0,earliest-start)/interval)*interval;
  if(slot<=end)return slot;
  day=new Date(Date.parse(day+'T00:00:00Z')+86400000).toISOString().slice(0,10);
 }
 return null;
}
