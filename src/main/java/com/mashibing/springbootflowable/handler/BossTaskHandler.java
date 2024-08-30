package com.mashibing.springbootflowable.handler;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * @author 谭老师
 * 你现在所学的每一项技能
 * 都是为了以后少说一句求人的话
 **/
//设置办理人与候选用户/组
public class BossTaskHandler implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        delegateTask.setAssignee("老板");
    }
}
