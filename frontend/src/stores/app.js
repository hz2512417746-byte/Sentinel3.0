import {defineStore} from 'pinia'
import {ref,shallowRef,computed,watch} from 'vue'
import axios from 'axios'

export const useAppStore=defineStore('app',()=>{
  const logs=ref([])
  const alerts=ref([])
  const stats=ref({totalLogs:0,logs1h:0,fraudCount:0,highRisk:0,blocked:0,pendingAlerts:0})
  const lists=shallowRef({interceptUsers:[],banUsers:[],flaggedUsers:[]})
  const paused=ref(false)
  const perfHistory=ref([])
  let lastAlertTotal=0

  const logCount=computed(()=>logs.value.length)
  const alertCount=computed(()=>alerts.value.length)

  // WebSocket
  let ws=null, wsTimer=null
  const wsLogBuf=[], wsAlertBuf=[]

  function flushWs(){
    if(paused.value)return
    const seenLogs=new Set(logs.value.map(l=>l.logId))
    const seenAlerts=new Set(alerts.value.map(a=>a.alertId))
    for(const l of wsLogBuf){
      if(l&&l.logId&&!seenLogs.has(l.logId)){logs.value.unshift(l);seenLogs.add(l.logId)}
    }
    for(const a of wsAlertBuf){
      if(a&&a.alertId&&!seenAlerts.has(a.alertId)){alerts.value.unshift(a);seenAlerts.add(a.alertId)}
    }
    wsLogBuf.length=0; wsAlertBuf.length=0
    if(logs.value.length>500)logs.value.length=500
    if(alerts.value.length>50)alerts.value.length=50
  }

  function connectWs(){
    if(ws&&ws.readyState===WebSocket.OPEN)return
    try{
      ws=new WebSocket('ws://localhost:8080/ws/logs')
      ws.onopen=()=>console.log('[WS] 原生连接已建立')
      ws.onmessage=e=>{
        try{
          if(paused.value)return
          const msg=JSON.parse(e.data)
          if(!msg||!msg.type||!msg.data)return
          if(msg.type==='log') wsLogBuf.push(msg.data)
          else if(msg.type==='alert') wsAlertBuf.push(msg.data)
        }catch(ex){}
      }
      ws.onclose=()=>{console.log('[WS] 断开, 3s后重连');setTimeout(connectWs,3000)}
      ws.onerror=()=>ws&&ws.close()
    }catch(e){setTimeout(connectWs,3000)}
  }

  function disconnectWs(){
    if(wsTimer){clearInterval(wsTimer);wsTimer=null}
    if(ws){try{ws.onclose=null;ws.close()}catch(e){};ws=null}
  }

  // HTTP 轮询
  async function fetchStats(){if(paused.value)return;try{const{data}=await axios.get('/api/stats');stats.value=data}catch(e){}}
  async function fetchLogs(){
    if(paused.value)return
    try{
      const{data}=await axios.get('/api/logs?pageSize=50')
      const arr=Array.isArray(data.content)?data.content:(Array.isArray(data)?data:[])
      const ex=new Set(logs.value.map(l=>l.logId));let added=0;let totalRisk=0;let riskCount=0
      for(const l of arr){if(l&&l.logId&&!ex.has(l.logId)){logs.value.unshift(l);added++;if(l.riskScore){totalRisk+=l.riskScore;riskCount++}}}
      if(logs.value.length>500)logs.value.length=500
      if(!paused.value){
        const ss=new Date().toLocaleTimeString()
        const delta=Math.max(0,alerts.value.length-lastAlertTotal)
        lastAlertTotal=alerts.value.length
        perfHistory.value.push([ss,added,delta,0,(riskCount>0?Math.round(totalRisk/riskCount*100)/100:0)])
        if(perfHistory.value.length>120)perfHistory.value.shift()
      }
    }catch(e){}
  }
  async function fetchLatency(){
    if(paused.value)return
    try{
      const{data}=await axios.get('/api/pipeline/metrics')
      const avgMs=data.stats?.avg_total_ms||0
      const last=perfHistory.value[perfHistory.value.length-1]
      if(last&&avgMs>0) last[3]=Math.round(avgMs*100)/100
    }catch(e){}
  }
  async function fetchAlerts(){
    if(paused.value)return
    try{
      const{data}=await axios.get('/api/alerts?status=pending&pageSize=30')
      const arr=Array.isArray(data)?data:[]
      // WebSocket 已推送的不覆盖
      const wsIds=new Set(alerts.value.map(a=>a.alertId))
      for(const a of arr){if(a&&a.alertId&&!wsIds.has(a.alertId)) alerts.value.unshift(a)}
      if(alerts.value.length>50)alerts.value.length=50
    }catch(e){}
  }
  async function fetchLists(){if(paused.value)return;try{const{data}=await axios.get('/api/lists');lists.value={interceptUsers:data.interceptUsers||[],banUsers:data.banUsers||[],flaggedUsers:data.flaggedUsers||[]}}catch(e){}}

  let timers=[]
  function startPolling(){
    connectWs()
    wsTimer=setInterval(flushWs,1000)
    fetchStats();fetchLogs();fetchLists().then(()=>fetchAlerts())
    timers=[setInterval(fetchStats,2000),setInterval(fetchLogs,2000),setInterval(()=>fetchLists().then(fetchAlerts),2000),setInterval(fetchLatency,2000)]
  }

  function removeAlert(alertId){alerts.value=alerts.value.filter(a=>a.alertId!==alertId)}
  async function banUser(uid){await axios.post('/api/lists/ban/'+uid);fetchLists()}
  async function interceptUser(uid){await axios.post('/api/lists/intercept/'+uid);fetchLists()}
  async function unbanUser(uid){await axios.post('/api/lists/unban/'+uid);fetchLists()}
  async function uninterceptUser(uid){await axios.post('/api/lists/unintercept/'+uid);fetchLists()}

  function stopPolling(){timers.forEach(clearInterval);timers=[];disconnectWs()}
  // 解暂停时立刻拉取最新数据补齐缺口
  watch(paused,(nv)=>{if(!nv){fetchStats();fetchLogs();fetchLists().then(()=>fetchAlerts())}})
  return {
    logs,alerts,stats,lists,paused,perfHistory,logCount,alertCount,
    fetchStats,fetchLogs,fetchAlerts,fetchLists,fetchLatency,
    startPolling,stopPolling,removeAlert,banUser,interceptUser,unbanUser,uninterceptUser,
  }
})
