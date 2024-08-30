package com.mashibing.springbootflowable.controller;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author 谭老师
 * 你现在所学的每一项技能
 * 都是为了以后少说一句求人的话
 **/
@Controller
@RequestMapping(value = "expense")
public class ExpenseController {
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    private HistoryService historyService;

/***************此处为业务代码******************/


    /**
     * 添加报销
     *
     * @param userId    用户Id
     * @param money     报销金额
     * @param descption 描述
     */
    @RequestMapping(value = "add")
    @ResponseBody
    public String addExpense(String userId, Integer money, String descption) {
        //启动流程
        HashMap<String, Object> map = new HashMap<>();
        map.put("taskUser", userId);
        map.put("money", money);
        map.put("days", 4);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("qingjia", map);
        return "提交成功.流程Id为：" + processInstance.getId();
    }

    /**
     * 获取审批管理列表
     */
    @RequestMapping(value = "/list")
    @ResponseBody
    public String list(String userId) {
        List<Task> tasks = taskService.createTaskQuery().taskAssignee(userId).orderByTaskCreateTime().desc().list();
        for (Task task : tasks) {
            System.out.println(task.toString());
        }
        return tasks.toArray().toString();
    }


    /**
     * 批准
     *
     * @param taskId 任务ID
     */
    @RequestMapping(value = "apply")
    @ResponseBody
    public String apply(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        //通过审核
        HashMap<String, Object> map = new HashMap<>();
        map.put("outcome", "通过");
        taskService.complete(taskId, map);
        return "processed ok!";
    }


    /**
     * 拒绝
     */
    @ResponseBody
    @RequestMapping(value = "reject")
    public String reject(String taskId) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("outcome", "驳回");
        taskService.complete(taskId, map);
        return "reject";
    }

    /**
     * 生成流程图
     *
     * @param processId 任务ID
     */
    @RequestMapping(value = "processDiagram")
    public void genProcessDiagram(HttpServletResponse httpServletResponse, String processId) throws Exception {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();

        //流程走完的不显示图
        if (pi == null) {
            return;
        }
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
        //使用流程实例ID，查询正在执行的执行对象表，返回流程实例对象
        String InstanceId = task.getProcessInstanceId();
        List<Execution> executions = runtimeService
                .createExecutionQuery()
                .processInstanceId(InstanceId)
                .list();

        //得到正在执行的Activity的Id
        List<String> activityIds = new ArrayList<>();
        List<String> flows = new ArrayList<>();
        for (Execution exe : executions) {
            List<String> ids = runtimeService.getActiveActivityIds(exe.getId());
            activityIds.addAll(ids);
        }

        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(pi.getProcessDefinitionId());
        ProcessEngineConfiguration engconf = processEngine.getProcessEngineConfiguration();
        ProcessDiagramGenerator diagramGenerator = engconf.getProcessDiagramGenerator();
        InputStream in = diagramGenerator.generateDiagram(bpmnModel, "png", activityIds, flows, engconf.getActivityFontName(), engconf.getLabelFontName(), engconf.getAnnotationFontName(), engconf.getClassLoader(), 1.0);
        OutputStream out = null;
        byte[] buf = new byte[1024];
        int legth = 0;
        try {
            out = httpServletResponse.getOutputStream();
            while ((legth = in.read(buf)) != -1) {
                out.write(buf, 0, legth);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     *  流程记录
     */
    @RequestMapping(value = "processRecordImage")
    public void processRecordImage(HttpServletResponse httpServletResponse, String processId) throws IOException {
        // 获取流程走向信息
        List<Map<String, String>> histActInsts = new ArrayList<>();
        // 连线信息
        List<Map<String, String>> flows = new ArrayList<>();
        // 高亮 流程节点
        List<String> activeActivityIds = new ArrayList<>();
        // 高亮连线ID
        List<String> highLightedFlows = new ArrayList<>();

        HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processId).singleResult();

        List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery()
                .orderByHistoricActivityInstanceStartTime().asc()
                .orderByHistoricActivityInstanceEndTime().asc()
                .processInstanceId(processId).list();

        //获取流程走向信息
        historicActivityInstances.forEach(hai -> {
            //如果存在 返回当前值 如果不存在 则返回Null
            Map<String, String> hihai = histActInsts.stream()
                    .filter(map -> map.get("id").equals(hai.getActivityId()))
                    .findFirst().orElse(null);
            if (hihai == null) {
                Map<String, String> hti = new HashMap<>();
                hti.put("id", hai.getActivityId());
                hti.put("name", hai.getActivityName());
                hti.put("type", hai.getActivityType());
                histActInsts.add(hti);
            } else {
                int index = histActInsts.indexOf(hihai);
                histActInsts.removeIf(map -> histActInsts.indexOf(map) > index);
            }
        });
//        所有节点都高亮
//        activeActivityIds = histActInsts.stream().map(map -> map.get("id")).collect(Collectors.toList());

        //当前节点高亮
        List<Task> taskList = taskService.createTaskQuery().processInstanceId(processId).list();
        for (Task obj : taskList) {
            ExecutionEntity executionEntity = (ExecutionEntity) runtimeService.createExecutionQuery()
                    .executionId(obj.getExecutionId()).singleResult();
            activeActivityIds.add(executionEntity.getActivityId());
        }

        for (int i = 0; i < histActInsts.size(); i++) {
            if (i == histActInsts.size()-1){
                break;
            }
            Map<String, String> flow = new HashMap<>();
            flow.put("source", histActInsts.get(i).get("id"));
            flow.put("target", histActInsts.get(i + 1).get("id"));
            flows.add(flow);
        }

        /* findFlowElementsOfTypeByProcInstId(processInstanceId, SequenceFlow.class).forEach(sequenceFlow -> {
            flows.forEach(flow -> {
                if (flow.get("source").equals(sequenceFlow.getSourceRef()) && flow.get("target").equals(sequenceFlow.getTargetRef())
                || flow.get("target").equals(sequenceFlow.getSourceRef()) && flow.get("source").equals(sequenceFlow.getTargetRef())){
                    highLightedFlows.add(sequenceFlow.getId());
                }
            });
        }); */

        BpmnModel bpmnModel = repositoryService.getBpmnModel(historicProcessInstance.getProcessDefinitionId());

        // 查询连接线信息-> update 2023-05-10
        Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
        List<SequenceFlow> elements = new ArrayList<>();
        for (FlowElement flowElement : flowElements) {
            if (flowElement instanceof SequenceFlow) {
                elements.add((SequenceFlow) flowElement);
            }
        }
        // 查询连接线信息结束 -> update 2023-05-10

        elements.forEach(sequenceFlow -> {
            flows.forEach(flow -> {
                if (flow.get("source").equals(sequenceFlow.getSourceRef()) && flow.get("target").equals(sequenceFlow.getTargetRef())
                        || flow.get("target").equals(sequenceFlow.getSourceRef()) && flow.get("source").equals(sequenceFlow.getTargetRef())) {
                    highLightedFlows.add(sequenceFlow.getId());
                }
            });
        });

        DefaultProcessDiagramGenerator defaultProcessDiagramGenerator = new DefaultProcessDiagramGenerator();
        //8. 转化成byte便于网络传输
        InputStream in = defaultProcessDiagramGenerator.generateDiagram(bpmnModel, "PNG", activeActivityIds, highLightedFlows, "宋体", "宋体", "宋体", null, 1.0);
        out(httpServletResponse, in);
    }

    public void out(HttpServletResponse httpServletResponse, InputStream in) throws IOException {
        OutputStream out = null;
        byte[] buf = new byte[1024];
        int legth = 0;
        try {
            out = httpServletResponse.getOutputStream();
            while ((legth = in.read(buf)) != -1) {
                out.write(buf, 0, legth);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }


}
