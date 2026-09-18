import {defineStore} from 'pinia'
import {ref,shallowRef,computed} from 'vue'
import axios from 'axios'

export const useAppStore=defineStore('app',()=>{
  const logs=ref([])
  const alerts=ref([])
  const stats=ref({totalLogs:0,logs1h:0,fraudCount:0,highRisk:0,blocked:0,pendingAlerts:0})
  const lists=shallowRef({interceptUsers:[],banUsers:[],flaggedUsers:[]})
  const paused=ref(false)
  const perfHistory=ref([])

  const logCount=computed(()=>logs.value.length)
  const alertCount=computed(()=>alerts.value.length)

  async function fetchStats(){try{const{data}=await axios.get('/api/stats');stats.value=data}catch(e){}}
  async function fetchLogs(){
    try{
      const{data}=await axios.get('/api/logs?pageSize=50')
      const arr=Array.isArray(data.content)?data.content:(Array.isArray(data)?data:[])
      const ex=new Set(logs.value.map(l=>l.logId));let added=0;let totalRisk=0;let riskCount=0
      for(const l of arr){if(l&&l.logId&&!ex.has(l.logId)){logs.value.unshift(l);added++;if(l.riskScore){totalRisk+=l.riskScore;riskCount++}}}
      if(logs.value.length>500)logs.value.length=500
      if(!paused.value){
        const ss=new Date().toLocaleTimeString()
        perfHistory.value.push([ss,added,alerts.value.length,0,(riskCount>0?Math.round(totalRisk/riskCount*100)/100:0)])
        if(perfHistory.value.length>60)perfHistory.value.shift()
      }
    }catch(e){}
  }
  async function fetchLatency(){
    try{
      const{data}=await axios.get('/api/pipeline/metrics')
      const avgMs=data.stats?.avg_total_ms||0
      const last=perfHistory.value[perfHistory.value.length-1]
      if(last&&avgMs>0) last[3]=Math.round(avgMs*100)/100
    }catch(e){}
  }
  async function fetchAlerts(){
    try{
      const{data}=await axios.get('/api/alerts?status=pending&pageSize=30')
      const arr=Array.isArray(data)?data:[]
      const ex=new Set(alerts.value.map(a=>a.alertId))
      for(const a of arr){if(a&&a.alertId&&!ex.has(a.alertId))alerts.value.unshift(a)}
      if(alerts.value.length>50)alerts.value.length=50
    }catch(e){}
  }
  async function fetchLists(){try{const{data}=await axios.get('/api/lists');lists.value={interceptUsers:data.interceptUsers||[],banUsers:data.banUsers||[],flaggedUsers:data.flaggedUsers||[]}}catch(e){}}

  let timers=[]
  function startPolling(){
    fetchStats();fetchLogs();fetchLists().then(()=>fetchAlerts())
    timers=[setInterval(fetchStats,3000),setInterval(fetchLogs,3000),setInterval(()=>fetchLists().then(fetchAlerts),5000),setInterval(fetchLatency,5000)]
  }

  function removeAlert(alertId){alerts.value=alerts.value.filter(a=>a.alertId!==alertId)}
  async function banUser(uid){await axios.post('/api/lists/ban/'+uid);fetchLists()}
  async function interceptUser(uid){await axios.post('/api/lists/intercept/'+uid);fetchLists()}
  async function unbanUser(uid){await axios.post('/api/lists/unban/'+uid);fetchLists()}
  async function uninterceptUser(uid){await axios.post('/api/lists/unintercept/'+uid);fetchLists()}

  function stopPolling(){timers.forEach(clearInterval);timers=[]}
  return {
    logs,alerts,stats,lists,paused,perfHistory,logCount,alertCount,
    fetchStats,fetchLogs,fetchAlerts,fetchLists,fetchLatency,
    startPolling,stopPolling,removeAlert,banUser,interceptUser,unbanUser,uninterceptUser,
    startPolling,removeAlert,banUser,interceptUser,unbanUser,uninterceptUser,
  }
})
