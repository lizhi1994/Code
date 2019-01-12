package com.tsingshan.web.controller.roster;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import com.tsingshan.common.annotation.Log;
import com.tsingshan.common.base.AjaxResult;
import com.tsingshan.common.enums.BusinessType;
import com.tsingshan.common.utils.DateUtils;
import com.tsingshan.common.utils.ExcelUtil;
import com.tsingshan.common.utils.StringUtils;
import com.tsingshan.framework.util.ShiroUtils;
import com.tsingshan.infomana.domain.Contract;
import com.tsingshan.infomana.domain.Employee;
import com.tsingshan.infomana.service.IContractService;
import com.tsingshan.infomana.service.IEmployeeService;
import com.tsingshan.system.service.IJobService;
import com.tsingshan.system.service.ISysPostService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.tsingshan.infomana.domain.Dimission;
import com.tsingshan.infomana.service.IDimissionService;
import com.tsingshan.web.core.base.BaseController;
import com.tsingshan.framework.web.page.TableDataInfo;

/**
 * 离职 信息操作处理
 * 
 * @author tsingshan
 * @date 2018-10-31
 */
@Controller
@RequestMapping("/roster/dimission")
public class DimissionController extends BaseController
{
    private static final Logger logger = LoggerFactory.getLogger(DimissionController.class);

    private String prefix = "roster/dimission";
	
	@Autowired
	private IDimissionService dimissionService;

	@Autowired
	private IEmployeeService employeeService;

	@Autowired
	private ISysPostService postService;

	@Autowired
	private IJobService jobService;

	@Autowired
    private IContractService contractService;
	
	@RequiresPermissions("roster:dimission:view")
	@GetMapping()
	public String dimission()
	{
	    return prefix + "/dimission";
	}
	
	/**
	 * 查询离职列表
	 */
	@RequiresPermissions("roster:dimission:list")
	@PostMapping("/list")
	@ResponseBody
	public TableDataInfo list(Dimission dimission)
	{
		startPage();
        List<Dimission> list = dimissionService.selectDimissionList(dimission);
		return getDataTable(list);
	}
	
	/**
	 * 新增离职
	 */
	@GetMapping("/add")
	public String add(ModelMap mmap)
	{
		mmap.put("posts", postService.selectPostAll());
		mmap.put("jobs", jobService.selectJobAll());
	    return prefix + "/add";
	}
	
	/**
	 * 新增保存离职
	 */
	@RequiresPermissions("roster:dimission:add")
	@Log(title = "离职", businessType = BusinessType.INSERT)
	@PostMapping("/add")
	@ResponseBody
	public AjaxResult addSave(Dimission dimission)
	{
        try {
            if (StringUtils.isEmpty(dimission.getEmployeeNo())) {
                return AjaxResult.error("工号不能为空");
            } else if (dimission.getDimissionDate() == null) {
                return AjaxResult.error("离职日期不能为空");
            } else if (StringUtils.isEmpty(dimission.getDimissionType())) {
                return AjaxResult.error("离职类型不能为空");
            } else if (StringUtils.isEmpty(dimission.getDimissionReason())) {
                return AjaxResult.error("离职原因不能为空");
            }
            dimission.setCreateBy(ShiroUtils.getLoginName());
            Long contractId = selectContractByEmployeeId(dimission.getEmployeeId());
            dimission.setContractId(contractId);
            dimissionService.insertDimission(dimission);
            long daySub = DateUtils.getDaySub(dimission.getDimissionDate(), DateUtils.getDate(), "yyyy-MM-dd");
            if (daySub > 0 ) {
                //更新员工信息表
                updateEmployee(dimission.getEmployeeId(),"1");
                //更新合同信息
                updateContract(dimission.getContractId(),dimission.getDimissionDate(),"1");
            } else {
                //更新合同信息
                updateContract(dimission.getContractId(),dimission.getDimissionDate(),"0");
            }
            return AjaxResult.success();
        } catch (Exception e) {
            logger.error("添加异常{}", e.getMessage());
            return AjaxResult.error("添加异常");
        }

	}

    /**
     * 根据工号获取 当前有效合同号 ID
     * @param employeeId
     * @return
     */
	private Long selectContractByEmployeeId(long employeeId) {
        List<Contract> contractList = contractService.selectContractListByEmployeeId(employeeId);
        for (Contract con : contractList) {
            if (con.getState().equals("0")) {
                return con.getContractId();
            }
        }
        return null;
    }

    /**
     * 根据员工ID更新合同
     * @param employeeId
     * @param flag
     */
    private void updateEmployee(long employeeId,String flag) {
        Employee employee = employeeService.selectEmployeeById(employeeId);
        employee.setState(flag);
        employee.setContractStatus(flag);
        employeeService.updateEmployee(employee);
        logger.info("离职员工更新完成 updateEmployee");
    }

    /**
     * 根据合同ID更新合同
     * @param contractId
     * @param expireDate
     * @param flag
     */
    private void updateContract(long contractId, String expireDate,String flag) {
        Contract contract = contractService.selectContractById(contractId);
        contract.setState(flag);
        contract.setExpireDate(expireDate);
        contractService.updateContract(contract);
        logger.info("离职合同更新完成 updateContract");
    }

	/**
	 * 修改离职
	 */
	@GetMapping("/edit/{id}")
	public String edit(@PathVariable("id") Long id, ModelMap mmap)
	{
		Dimission dimission = dimissionService.selectDimissionById(id);
		mmap.put("dimission", dimission);
	    return prefix + "/edit";
	}
	
	/**
	 * 修改保存离职
	 */
	@RequiresPermissions("roster:dimission:edit")
	@Log(title = "离职", businessType = BusinessType.UPDATE)
	@PostMapping("/edit")
	@ResponseBody
	public AjaxResult editSave(Dimission dimission)
	{

        dimission.setUpdateBy(ShiroUtils.getLoginName());

        long daySub = DateUtils.getDaySub(dimission.getDimissionDate(), DateUtils.getDate(), "yyyy-MM-dd");
        String flag = (daySub > 0 ? "1" : "0");

        updateEmployee(dimission.getEmployeeId(),flag);
        //更新合同信息
        updateContract(dimission.getContractId(),dimission.getDimissionDate(),flag);

		AjaxResult ajaxResult = dimissionService.updateDimission(dimission);
		return ajaxResult;
	}
	
	/**
	 * 删除离职
	 */
	@RequiresPermissions("roster:dimission:remove")
	@Log(title = "离职", businessType = BusinessType.DELETE)
	@PostMapping( "/remove")
	@ResponseBody
	public AjaxResult remove(String ids)
	{
		AjaxResult ajaxResult = dimissionService.deleteDimissionByIds(ids);
		return ajaxResult;

	}

	@Log(title = "离职", businessType = BusinessType.EXPORT)
	@RequiresPermissions("roster:dimission:export")
	@PostMapping("/export")
	@ResponseBody
	public AjaxResult export(String  ids)
	{
		List<Dimission> list = dimissionService.selectExport(ids);
		ExcelUtil<Dimission> util = new ExcelUtil<Dimission>(Dimission.class);
		return util.exportExcel(list, "dimission");
	}
	
}
