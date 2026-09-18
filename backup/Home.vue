<template>
  <div>
    <h2 style="margin-bottom:16px;font-size:18px;font-weight:600">总览仪表盘</h2>
    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="4" v-for="s in statCards" :key="s.label"><el-card shadow="hover"><div style="text-align:center;font-size:24px;font-weight:700">{{s.value}}</div><div style="font-size:11px;color:#909399;margin-top:4px">{{s.label}}</div></el-card></el-col>
    </el-row>
    <el-row :gutter="12" style="margin-bottom:14px">
      <el-col :span="16">
        <div style="display:flex;flex-direction:column;gap:12px">
          <el-card header="系统实时监控 — 流量 / 告警 / 风险"><div id="ch1" style="height:200px"/></el-card>
          <el-card header="处理延迟"><div id="ch3" style="height:120px"/></el-card>
        </div>
      </el-col>
      <el-col :span="8"><el-card header="决策分布"><div id="ch2" style="height:340px"/></el-card></el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :span="16">
        <el-card>
          <template #header><div style="display:flex;justify-content:space-between"><span>实时日志流</span><el-switch v-model="store.paused" size="small"/></div></template>
          <el-table :data="store.logs" size="small" max-height="340" stripe @row-click="showDetail">
            <el-table-column label="时间" width="85"><template #default="{r}">{{(r&&r.timestamp||'').slice(11,19)}}</template></el-table-column>
            <el-table-column prop="userId" label="用户" width="95"/><el-table-column prop="eventType" label="事件" width="95"/>
            <el-table-column label="风险分" width="75"><template #default="{r}"><span :style="{color:rk(r&&r.riskScore),fontWeight:700}">{{(r&&r.riskScore||0).toFixed(2)}}</span></template></el-table-column>
            <el-table-column label="决策" width="70"><template #default="{r}"><el-tag :type="(r&&r.decision)==='block'?'danger':'success'" size="small">{{(r&&r.decision)==='block'?'阻断':'放行'}}</el-tag></template></el-table-column>
            <el-table-column label="命中规则" min-width="180"><template #default="{r}"><span style="font-size:11px;white-space:pre-line;color:#e6a23c">{{(r&&r.hitRules||'').slice(0,80)}}</span></template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card style="height:420px" body-style="overflow-y:auto;height:360px">
          <template #header><div style="display:flex;justify-content:space-between"><span>风险告警</span><router-link to="/alerts?from=home" style="color:#909399;text-decoration:none;font-size:14px">→</router-link></div></template>
          <div v-if="!store.alerts.length" style="text-align:center;padding:60px;color:#909399">暂无告警</div>
          <div v-for="a in store.alerts" :key="a.alertId" style="padding:10px 12px;margin-bottom:6px;border:1px solid #ebeef5;border-radius:6px;background:#fff">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
              <div>
                <b style="font-size:13px;cursor:pointer" @click="openAlert(a)">{{a.userId}}</b>
                <span style="font-size:11px;color:#909399;margin-left:6px">{{a.title||''}}</span>
              </div>
              <el-tag :type="a.alertLevel>=3?'danger':'warning'" size="small">{{a.alertLevel>=3?'紧急':'高危'}}</el-tag>
            </div>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <div>
                <span style="font-family:monospace;font-weight:700;font-size:12px;color:#f56c6c">{{(a.riskScore||0).toFixed(2)}}</span>
                <span style="font-size:10px;color:#909399;margin-left:6px">{{(a.detail||'').slice(0,40)}}</span>
              </div>
              <div style="display:flex;gap:4px">
                <el-button size="small" type="danger" @click.stop="quickBan(a)">封号</el-button>
                <el-button size="small" type="warning" @click.stop="quickIntercept(a)">拦截</el-button>
                <el-button size="small" @click.stop="openAlert(a)">详情</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="dlg" title="日志详情" width="680px">
      <div v-if="dlgRow" style="line-height:2.2;font-size:13px">
        <p><b>用户：</b>{{dlgRow.userId}} &nbsp; <b>事件：</b>{{dlgRow.eventType}} &nbsp; <b>IP：</b>{{dlgRow.srcIp}}</p>
        <p><b>风险分：</b><span :style="{color:rk(dlgRow.riskScore),fontWeight:700}">{{(dlgRow.riskScore||0).toFixed(4)}}</span></p>
        <p><b>ML分：</b>{{(dlgRow.mlScore||0).toFixed(4)}} &nbsp; <b>规则分：</b>{{(dlgRow.ruleScore||0).toFixed(4)}}</p>
        <el-divider/>
        <pre style="white-space:pre-line;color:#e6a23c;margin:0">{{dlgRow.hitRules||'无命中规则'}}</pre>
      </div>
      <template #footer>
        <el-button v-if="!isBanned" type="danger" @click="doBan">封号（禁交易+禁登录）</el-button>
        <el-button v-if="!isBanned" type="warning" @click="doIntercept">拦截本次</el-button>
        <el-button v-if="isBanned" @click="doUnban">解封</el-button>
        <el-button @click="dlg=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import {ref,computed,onMounted,onUnmounted} from 'vue';import * as echarts from 'echarts';import {ElMessage} from 'element-plus'
import {useAppStore} from '../stores/app'
const store=useAppStore()
const dlg=ref(false),dlgRow=ref(null),af=ref({'1h':0,'24h':0,'7d':0});let c1,c2,c3
async function fetchAF(){try{const data=await fetch('/api/alerts/frequency').then(r=>r.json());if(data)af.value=data}catch(e){}}
const rk=s=>(s||0)>=0.7?'#f56c6c':(s||0)>=0.5?'#e6a23c':'#67c23a'
const isBanned=computed(()=>{
  const uid=dlgRow.value?.userId
  return uid && (store.lists.banUsers||[]).includes(uid)
})
const statCards=computed(()=>[
  {label:'总日志',value:(store.stats.totalLogs||0).toLocaleString()},{label:'告警(1h)',value:af.value['1h']||0},
  {label:'欺诈',value:store.stats.fraudCount||0},{label:'高风险',value:store.stats.highRisk||0},
  {label:'已拦截',value:store.stats.blocked||0},{label:'告警(24h)',value:af.value['24h']||0},
])
function showDetail(r){dlgRow.value=r;dlg.value=true}
async function openAlert(a){if(a&&a.logId){try{const res=await fetch('/api/logs/'+a.logId);const data=await res.json();dlgRow.value=data}catch(e){dlgRow.value=a}}else{dlgRow.value=a}dlg.value=true}
async function doBan(){const u=dlgRow.value?.userId;if(!u)return;await store.banUser(u);ElMessage.success('已封号 — 禁止交易和登录');dlg.value=false}
async function doUnban(){const u=dlgRow.value?.userId;if(!u)return;await store.unbanUser(u);ElMessage.success('已解封 — 恢复所有操作');dlg.value=false}
async function doIntercept(){const u=dlgRow.value?.userId;if(!u)return;await store.banUser(u);ElMessage.success('已拦截');dlg.value=false}
async function quickBan(a){await store.banUser(a.userId);store.removeAlert(a.alertId);ElMessage.success('已封号 '+a.userId)}
async function quickIntercept(a){await store.interceptUser(a.userId);store.removeAlert(a.alertId);ElMessage.success('已拦截 '+a.userId)}
function draw(){
  if(!c1||!c3||c1.isDisposed()||c3.isDisposed())return
  const d=store.perfHistory.slice(-30)
  const t=d.length?d.map(r=>r[0]):[new Date().toLocaleTimeString().slice(0,5)]
  const tr=d.length?d.map(r=>r[1]||0):[0]
  const al=d.length?d.map(r=>r[2]||0):[0]
  const la=d.length?d.map(r=>r[3]||0):[0]
  const ri=d.length?d.map(r=>r[4]||0):[0]
  // 上图：流量+告警+风险
  c1.setOption({
    tooltip:{trigger:'axis'},legend:{data:['流量','告警','风险'],textStyle:{fontSize:10},top:0},
    grid:{top:30,right:15,bottom:15,left:40},
    xAxis:{type:'category',data:t,axisLabel:{fontSize:8,interval:9}},
    yAxis:{type:'value',min:0,axisLabel:{fontSize:8}},
    series:[
      {name:'流量',type:'line',data:tr,smooth:!0,lineStyle:{color:'#409eff',width:2},symbol:'none'},
      {name:'告警',type:'line',data:al,smooth:!0,lineStyle:{color:'#e6a23c',width:2},symbol:'none'},
      {name:'风险',type:'line',data:ri,smooth:!0,lineStyle:{color:'#67c23a',width:1.5},symbol:'none'},
    ]
  })
  // 下图：延迟
  c3.setOption({
    tooltip:{trigger:'axis'},grid:{top:10,right:15,bottom:15,left:40},
    xAxis:{type:'category',data:t,axisLabel:{fontSize:8,interval:9}},
    yAxis:{type:'value',name:'ms',min:0,axisLabel:{fontSize:8}},
    series:[{name:'延迟',type:'line',data:la,smooth:!0,lineStyle:{color:'#f56c6c',width:2},areaStyle:{color:'rgba(245,108,108,.1)'},symbol:'none'}]
  })
  const pass=Math.max(1,(store.stats.totalLogs||1)-(store.stats.highRisk||0))
  const risk=Math.max(0,store.stats.highRisk||0)
  if(c2&&!c2.isDisposed())c2.setOption({tooltip:{trigger:'item',formatter:'{b}: {c} ({d}%)'},series:[{type:'pie',radius:['55%','80%'],label:{formatter:'{b}\n{d}%',fontSize:12},data:[{name:'放行',value:pass,itemStyle:{color:'#67c23a'}},{name:'干预',value:risk,itemStyle:{color:'#f56c6c'}}]}]})
}
onMounted(()=>{store.startPolling();fetchAF();setInterval(fetchAF,10000);setTimeout(()=>{const d1=document.getElementById('ch1'),d2=document.getElementById('ch2'),d3=document.getElementById('ch3');if(d1)c1=echarts.init(d1);if(d2)c2=echarts.init(d2);if(d3)c3=echarts.init(d3);draw();setInterval(draw,3000)},800)})
onUnmounted(()=>{if(store.stopPolling)store.stopPolling();if(c1&&!c1.isDisposed())c1.dispose();if(c2&&!c2.isDisposed())c2.dispose();if(c3&&!c3.isDisposed())c3.dispose()})
</script>
