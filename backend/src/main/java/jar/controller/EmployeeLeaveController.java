package jar.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

import jar.entity.EmployeeLeave;
import jar.service.EmployeeLeaveService;
import jar.service.AIService;

@RestController
@RequestMapping("/api/leaves")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "https://employee-leave-management-lake.vercel.app/"
})
public class EmployeeLeaveController {

    private final EmployeeLeaveService service;
    private final AIService aiService;

    // Constructor Injection (Recommended)
    public EmployeeLeaveController(EmployeeLeaveService service, AIService aiService) {
        this.service = service;
        this.aiService = aiService;
    }

    // CREATE: employee and admin can create
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public EmployeeLeave createLeave(@RequestBody EmployeeLeave leave, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("EMPLOYEE"))) {
            leave.setEmployeeName(authentication.getName());
        }
        return service.createLeave(leave);
    }

    // READ (Get All) - admin only
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<EmployeeLeave> getAllLeaves() {
        return service.getAllLeaves();
    }

    // READ (Own leaves) - employee/admin
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public List<EmployeeLeave> getMyLeaves(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Unauthenticated");
        }
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("ADMIN"))) {
            return service.getAllLeaves();
        }
        return service.getLeavesByEmployeeName(authentication.getName());
    }

    // UPDATE - admin only
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeLeave updateLeave(@PathVariable Long id,
                                     @RequestBody EmployeeLeave leave) {
        return service.updateLeave(id, leave);
    }

    // DELETE - admin only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteLeave(@PathVariable Long id) {
        service.deleteLeave(id);
        return "Leave deleted successfully!";
    }

    // AI Detailed Prediction for leave approval
    @PostMapping("/predict-detailed")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> predictApprovalDetailed(@RequestBody EmployeeLeave leave) {
        return aiService.predictApprovalDetailed(leave);
    }

    // AI Prediction for leave approval
    @PostMapping("/predict")
    @PreAuthorize("hasRole('ADMIN')")
    public String predictApproval(@RequestBody EmployeeLeave leave) {
        return aiService.predictApproval(leave);
    }

    // Chatbot endpoint
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')")
    public String chatWithAI(@RequestBody Map<String, String> request, Authentication authentication) {
        String message = request.get("message");
        String employeeName = authentication.getName();
        return aiService.chatWithAI(message, employeeName);
    }

    // AI Trends analysis
    @GetMapping("/trends")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getTrends() {
        return aiService.analyzeTrends();
    }

    // Employee analysis
    @GetMapping("/employee-analysis/{employeeName}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getEmployeeAnalysis(@PathVariable String employeeName) {
        return aiService.getEmployeeAnalysis(employeeName);
    }

    // Team shortage alerts
    @GetMapping("/team-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> getTeamAlerts() {
        return aiService.getTeamShortageAlerts();
    }

    // HR risk alerts
    @GetMapping("/hr-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> getHRAlerts() {
        return aiService.getHRAlerts();
    }
}