package com.wz.job.handler.service;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.utils.ReferenceConfigCache;
import com.alibaba.dubbo.rpc.service.GenericService;
import com.wz.job.common.mapper.JobLogMapper;
import com.wz.job.common.mapper.JobTaskMapper;
import com.wz.job.common.model.JobLog;
import com.wz.job.common.model.JobTask;
import com.wz.job.common.utils.Constants;
import com.wz.job.handler.JobHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>处理Dubbo类型的任务</p>
 * Created by wangzi on 2017-09-22.
 */
@Slf4j
@JobHandlerService(Constants.TYPE_DUBBO)
public class DubboJobHandler implements Job {
    @Autowired
    private JobTaskMapper taskMapper;
    @Autowired
    private JobLogMapper logMapper;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobTask task = (JobTask) context.getJobDetail().getJobDataMap().get("job");
        log.info("execute job {}", task.getJobName());
        JobLogMapper mapper = (JobLogMapper) context.getJobDetail().getJobDataMap().get("mapper");
        JobLog jobLog = null;
        List<JobLog> logs = mapper.queryLogsByJobId(task.getJobId());
        if (logs != null && logs.size() == Constants.DEFAULT_LOGSIZE) {
            jobLog = Collections.min(logs);
        }
        boolean flag = false;
        String result = "no result";
        if (jobLog == null) {
            flag = true;
            jobLog = new JobLog();
            jobLog.setJobId(task.getJobId());
        }
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setApplication(new ApplicationConfig(task.getDubboAppName()));
        reference.setInterface(task.getSelectMethod().substring(0, task.getSelectMethod().lastIndexOf(".")));
        if (StringUtils.isNotBlank(task.getDubboAppVersion())) {
            reference.setVersion(task.getDubboAppVersion());
        }
        reference.setProtocol(task.getDubboAppProtocol());
        reference.setRegistry(new RegistryConfig(task.getZkAddress()));
        reference.setGeneric(true);
        ReferenceConfigCache cache = ReferenceConfigCache.getCache();
        try {
            GenericService genericService = cache.get(reference);
            Object datas;
            if (StringUtils.isNotBlank(task.getParams())) {
                datas = genericService.$invoke(task.getSelectMethod().substring(task.getSelectMethod().lastIndexOf(".") + 1),
                        new String[]{"java.lang.String"}, new Object[]{task.getParams()});
            } else {
                datas = genericService.$invoke(task.getSelectMethod().substring(task.getSelectMethod().lastIndexOf(".") + 1),
                        new String[]{}, new Object[]{});
            }
            List<Object> list = (ArrayList) datas;
            if (datas != null && list.size() > 0) {
                if (list.size() > Constants.MAX_JOB_COUNT) {
                    int div = list.size() % Constants.MAX_JOB_COUNT;
                    int round = div == 0 ? list.size() / Constants.MAX_JOB_COUNT : list.size() / Constants.MAX_JOB_COUNT + 1; //循环次数
                    for (int i = 0; i < round; i++) {
                        List split = list.stream().skip(i * Constants.MAX_JOB_COUNT).limit(Constants.MAX_JOB_COUNT).collect(Collectors.toList());
                        result = String.valueOf(genericService.$invoke(task.getExecuteMethod().substring(task.getExecuteMethod().lastIndexOf(".") + 1),
                                new String[]{List.class.getName()}, new Object[]{split}));
                        jobLog.setExecuteResult(result);
                        saveLog(mapper, jobLog, flag);
                    }
                } else {
                    result = String.valueOf(genericService.$invoke(task.getExecuteMethod().substring(task.getExecuteMethod().lastIndexOf(".") + 1),
                            new String[]{List.class.getName()}, new Object[]{list}));
                    jobLog.setExecuteResult(result);
                    saveLog(mapper, jobLog, flag);
                }
            }
            jobLog.setExecuteResult(result);
            saveLog(mapper, jobLog, flag);
        } catch (Exception e) {
            log.error("execute job error {}", e.getMessage());
            jobLog.setExecuteResult(e.getMessage());
            saveLog(mapper, jobLog, flag);
        }
    }

    private void saveLog(JobLogMapper mapper, JobLog jobLog, boolean saveFlag) {
        if (saveFlag) {
            mapper.insertLog(jobLog);
        } else {
            mapper.updateLog(jobLog);
        }
    }
}
