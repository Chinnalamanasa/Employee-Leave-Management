package jar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import jar.entity.EmployeeLeave;
import jar.repository.EmployeeLeaveRepository;

@Service
public class AIService {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final EmployeeLeaveRepository leaveRepository;

    public AIService(EmployeeLeaveRepository leaveRepository) {
        this.leaveRepository = leaveRepository;
    }

    // Enhanced AI Prediction with detailed analysis
    public Map<String, Object> predictApprovalDetailed(EmployeeLeave leave) {
        Map<String, Object> result = new HashMap<>();
        List<EmployeeLeave> employeeLeaves = leaveRepository.findByEmployeeName(leave.getEmployeeName());
        List<EmployeeLeave> allLeaves = leaveRepository.findAll();

        // Basic metrics
        int totalLeaves = employeeLeaves.size();
        long approvedLeaves = employeeLeaves.stream().filter(l -> "Approved".equals(l.getStatus())).count();
        long rejectedLeaves = employeeLeaves.stream().filter(l -> "Rejected".equals(l.getStatus())).count();
        long pendingLeaves = employeeLeaves.stream().filter(l -> "Pending".equals(l.getStatus())).count();

        // Pattern analysis
        Map<String, Object> patterns = analyzePatterns(employeeLeaves, leave);

        // Risk assessment
        Map<String, Object> risks = assessRisks(employeeLeaves, allLeaves, leave);

        // Team impact
        Map<String, Object> teamImpact = analyzeTeamImpact(allLeaves, leave);

        // Calculate approval probability
        double approvalProbability = calculateApprovalProbability(employeeLeaves, patterns, risks);

        // Generate recommendation
        String recommendation = generateRecommendation(approvalProbability, patterns, risks, teamImpact);

        result.put("employeeName", leave.getEmployeeName());
        result.put("totalLeaves", totalLeaves);
        result.put("approvedLeaves", approvedLeaves);
        result.put("rejectedLeaves", rejectedLeaves);
        result.put("pendingLeaves", pendingLeaves);
        result.put("approvalProbability", Math.round(approvalProbability * 100.0) / 100.0);
        result.put("recommendation", recommendation);
        result.put("patterns", patterns);
        result.put("risks", risks);
        result.put("teamImpact", teamImpact);
        result.put("confidence", calculateConfidence(totalLeaves, patterns));

        return result;
    }

    // Legacy method for backward compatibility
    public String predictApproval(EmployeeLeave leave) {
        Map<String, Object> detailed = predictApprovalDetailed(leave);
        return (String) detailed.get("recommendation");
    }

    private Map<String, Object> analyzePatterns(List<EmployeeLeave> employeeLeaves, EmployeeLeave currentLeave) {
        Map<String, Object> patterns = new HashMap<>();

        if (employeeLeaves.isEmpty()) {
            patterns.put("isFirstTime", true);
            patterns.put("patternType", "New Employee");
            return patterns;
        }

        // Day of week patterns
        Map<String, Long> dayPatterns = employeeLeaves.stream()
            .collect(Collectors.groupingBy(
                l -> l.getStartDate().getDayOfWeek().toString(),
                Collectors.counting()
            ));

        String mostCommonDay = dayPatterns.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");

        long mostCommonDayCount = dayPatterns.getOrDefault(mostCommonDay, 0L);
        double dayFrequency = (double) mostCommonDayCount / employeeLeaves.size();

        // Monthly patterns
        Map<String, Long> monthlyPatterns = employeeLeaves.stream()
            .collect(Collectors.groupingBy(
                l -> l.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.counting()
            ));

        // Consecutive days pattern
        long multiDayLeaves = employeeLeaves.stream()
            .filter(l -> ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) > 0)
            .count();

        // Recent activity (last 30 days)
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long recentLeaves = employeeLeaves.stream()
            .filter(l -> l.getStartDate().isAfter(thirtyDaysAgo))
            .count();

        patterns.put("mostCommonDay", mostCommonDay);
        patterns.put("dayFrequency", Math.round(dayFrequency * 100.0) / 100.0);
        patterns.put("monthlyPatterns", monthlyPatterns);
        patterns.put("multiDayLeaves", multiDayLeaves);
        patterns.put("recentLeaves", recentLeaves);
        patterns.put("isPatternDetected", dayFrequency > 0.4); // 40% same day
        patterns.put("patternType", determinePatternType(dayFrequency, recentLeaves, employeeLeaves.size()));

        return patterns;
    }

    private String determinePatternType(double dayFrequency, long recentLeaves, int totalLeaves) {
        if (dayFrequency > 0.5) return "High Day Frequency";
        if (recentLeaves > 2) return "Recent Activity";
        if (totalLeaves > 10) return "High Usage";
        if (totalLeaves < 3) return "Low Usage";
        return "Normal Pattern";
    }

    private Map<String, Object> assessRisks(List<EmployeeLeave> employeeLeaves, List<EmployeeLeave> allLeaves, EmployeeLeave currentLeave) {
        Map<String, Object> risks = new HashMap<>();

        // High usage risk
        boolean highUsage = employeeLeaves.size() > 8;
        risks.put("highUsage", highUsage);

        // Suspicious patterns
        Map<String, Object> patterns = analyzePatterns(employeeLeaves, currentLeave);
        boolean suspiciousPattern = (Boolean) patterns.get("isPatternDetected");
        risks.put("suspiciousPattern", suspiciousPattern);

        // Recent rejections
        long recentRejections = employeeLeaves.stream()
            .filter(l -> "Rejected".equals(l.getStatus()))
            .filter(l -> l.getStartDate().isAfter(LocalDate.now().minusDays(90)))
            .count();
        risks.put("recentRejections", recentRejections);
        risks.put("highRejectionRate", recentRejections > 2);

        // Overlapping leaves (team impact)
        LocalDate start = currentLeave.getStartDate();
        LocalDate end = currentLeave.getEndDate();
        long overlappingLeaves = allLeaves.stream()
            .filter(l -> !l.getEmployeeName().equals(currentLeave.getEmployeeName()))
            .filter(l -> "Approved".equals(l.getStatus()) || "Pending".equals(l.getStatus()))
            .filter(l -> !(l.getEndDate().isBefore(start) || l.getStartDate().isAfter(end)))
            .count();
        risks.put("overlappingLeaves", overlappingLeaves);
        risks.put("teamShortage", overlappingLeaves > 3);

        // Calculate overall risk score
        double riskScore = 0.0;
        if (highUsage) riskScore += 0.3;
        if (suspiciousPattern) riskScore += 0.25;
        if (recentRejections > 2) riskScore += 0.2;
        if (overlappingLeaves > 3) riskScore += 0.25;

        risks.put("riskScore", Math.round(riskScore * 100.0) / 100.0);
        risks.put("riskLevel", riskScore > 0.6 ? "HIGH" : riskScore > 0.3 ? "MEDIUM" : "LOW");

        return risks;
    }

    private Map<String, Object> analyzeTeamImpact(List<EmployeeLeave> allLeaves, EmployeeLeave currentLeave) {
        Map<String, Object> impact = new HashMap<>();

        LocalDate start = currentLeave.getStartDate();
        LocalDate end = currentLeave.getEndDate();

        // Count overlapping approved leaves
        long overlappingApproved = allLeaves.stream()
            .filter(l -> !l.getEmployeeName().equals(currentLeave.getEmployeeName()))
            .filter(l -> "Approved".equals(l.getStatus()))
            .filter(l -> !(l.getEndDate().isBefore(start) || l.getStartDate().isAfter(end)))
            .count();

        // Count overlapping pending leaves
        long overlappingPending = allLeaves.stream()
            .filter(l -> !l.getEmployeeName().equals(currentLeave.getEmployeeName()))
            .filter(l -> "Pending".equals(l.getStatus()))
            .filter(l -> !(l.getEndDate().isBefore(start) || l.getStartDate().isAfter(end)))
            .count();

        // Calculate impact level
        int totalOverlapping = (int) (overlappingApproved + overlappingPending);
        String impactLevel = totalOverlapping > 5 ? "CRITICAL" : totalOverlapping > 3 ? "HIGH" : totalOverlapping > 1 ? "MEDIUM" : "LOW";

        impact.put("overlappingApproved", overlappingApproved);
        impact.put("overlappingPending", overlappingPending);
        impact.put("totalOverlapping", totalOverlapping);
        impact.put("impactLevel", impactLevel);
        impact.put("teamShortageWarning", totalOverlapping > 3);

        return impact;
    }

    private double calculateApprovalProbability(List<EmployeeLeave> employeeLeaves, Map<String, Object> patterns, Map<String, Object> risks) {
        if (employeeLeaves.isEmpty()) return 0.8; // New employees get benefit of doubt

        double baseProbability = 0.7; // Start with 70%

        // Adjust based on history
        long approved = employeeLeaves.stream().filter(l -> "Approved".equals(l.getStatus())).count();
        double approvalRate = employeeLeaves.isEmpty() ? 0 : (double) approved / employeeLeaves.size();
        baseProbability += (approvalRate - 0.5) * 0.3; // +/- 30% based on history

        // Adjust for risks
        double riskScore = (Double) risks.get("riskScore");
        baseProbability -= riskScore * 0.4; // Reduce probability based on risk

        // Adjust for patterns
        boolean suspiciousPattern = (Boolean) patterns.get("isPatternDetected");
        if (suspiciousPattern) baseProbability -= 0.2;

        // Ensure bounds
        return Math.max(0.1, Math.min(0.95, baseProbability));
    }

    private String generateRecommendation(double probability, Map<String, Object> patterns, Map<String, Object> risks, Map<String, Object> teamImpact) {
        String riskLevel = (String) risks.get("riskLevel");
        String impactLevel = (String) teamImpact.get("impactLevel");
        boolean teamShortage = (Boolean) teamImpact.get("teamShortageWarning");

        if (probability > 0.7 && riskLevel.equals("LOW") && !teamShortage) {
            return "✅ APPROVE: Leave appears genuine with low risk and minimal team impact.";
        } else if (probability > 0.5 && riskLevel.equals("MEDIUM")) {
            return "⚠️ REVIEW: Moderate risk detected. Consider approval with monitoring.";
        } else if (probability < 0.4 || riskLevel.equals("HIGH") || teamShortage) {
            return "❌ REJECT: High risk or team shortage detected. Recommend rejection.";
        } else {
            return "🤔 MANUAL REVIEW: Requires human assessment due to mixed signals.";
        }
    }

    private double calculateConfidence(int totalLeaves, Map<String, Object> patterns) {
        // Confidence increases with more data
        double baseConfidence = Math.min(0.9, totalLeaves / 10.0);
        boolean hasPattern = (Boolean) patterns.get("isPatternDetected");
        if (hasPattern) baseConfidence += 0.1;
        return Math.min(0.95, baseConfidence);
    }

    // Chatbot response using OpenAI
    public String chatWithAI(String userMessage, String employeeName) {
        if (openaiApiKey == null || openaiApiKey.isEmpty() || "dummy".equals(openaiApiKey)) {
            return getMockAIResponse(userMessage, employeeName);
        }

        // Get employee context
        List<EmployeeLeave> leaves = leaveRepository.findByEmployeeName(employeeName);
        int leaveCount = leaves.size();
        long pendingCount = leaves.stream().filter(l -> "Pending".equals(l.getStatus())).count();

        String context = String.format(
            "Employee %s has %d total leaves, %d pending. Available commands: apply leave, check leaves, leave history.",
            employeeName, leaveCount, pendingCount
        );

        String prompt = "You are a leave management assistant. " + context +
            " Respond helpfully to: " + userMessage +
            " If they want to apply leave, suggest format. If asking for info, provide it.";

        Map<String, Object> request = new HashMap<>();
        request.put("model", "gpt-3.5-turbo");
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("max_tokens", 150);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + openaiApiKey);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://api.openai.com/v1/chat/completions",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>(){}
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            return "Sorry, I couldn't process your request. Error: " + e.getMessage();
        }

        return "I'm sorry, I couldn't understand that.";
    }

    // Mock AI responses for demo purposes
    private String getMockAIResponse(String userMessage, String employeeName) {
        String message = userMessage.toLowerCase();

        // Get employee context
        List<EmployeeLeave> leaves = leaveRepository.findByEmployeeName(employeeName);
        int leaveCount = leaves.size();
        long pendingCount = leaves.stream().filter(l -> "Pending".equals(l.getStatus())).count();

        if (message.contains("apply") && message.contains("leave")) {
            return "To apply for leave, go to the 'Apply Leave' section and fill out the form with your leave type, start date, end date, and reason.";
        }

        if (message.contains("how many") || message.contains("balance") || message.contains("remaining")) {
            return String.format("You have %d total leave requests, with %d currently pending approval.", leaveCount, pendingCount);
        }

        if (message.contains("history") || message.contains("my leaves")) {
            return String.format("You have %d leave requests in total. You can view your complete leave history in the main dashboard.", leaveCount);
        }

        if (message.contains("status") || message.contains("pending")) {
            return String.format("You have %d pending leave requests waiting for approval.", pendingCount);
        }

        if (message.contains("hello") || message.contains("hi") || message.contains("hey")) {
            return String.format("Hello %s! I'm your AI Leave Assistant. I can help you with applying for leave, checking your leave balance, or viewing your leave history. What would you like to know?", employeeName);
        }

        if (message.contains("thank")) {
            return "You're welcome! Is there anything else I can help you with regarding your leave management?";
        }

        if (message.contains("bye") || message.contains("goodbye")) {
            return "Goodbye! Have a great day managing your leaves.";
        }

        // Default helpful response
        return "I'm here to help with your leave management! You can ask me about:\n• Applying for leave\n• Checking your leave balance\n• Viewing your leave history\n• Current pending requests\n\nWhat would you like to know?";
    }

    // Analyze leave trends
    public Map<String, Object> analyzeTrends() {
        List<EmployeeLeave> allLeaves = leaveRepository.findAll();
        Map<String, Object> trends = new HashMap<>();

        // Count by status
        long approved = allLeaves.stream().filter(l -> "Approved".equals(l.getStatus())).count();
        long rejected = allLeaves.stream().filter(l -> "Rejected".equals(l.getStatus())).count();
        long pending = allLeaves.stream().filter(l -> "Pending".equals(l.getStatus())).count();

        trends.put("totalLeaves", allLeaves.size());
        trends.put("approved", approved);
        trends.put("rejected", rejected);
        trends.put("pending", pending);
        trends.put("approvalRate", allLeaves.size() > 0 ? Math.round((double) approved / allLeaves.size() * 100.0) / 100.0 : 0.0);

        // Monthly trends
        Map<String, Long> monthlyTrends = allLeaves.stream()
            .collect(Collectors.groupingBy(
                l -> l.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.counting()
            ));
        trends.put("monthlyTrends", monthlyTrends);

        // Employee-wise analysis
        Map<String, Long> employeeStats = allLeaves.stream()
            .collect(Collectors.groupingBy(EmployeeLeave::getEmployeeName, Collectors.counting()));
        trends.put("employeeStats", employeeStats);

        // Peak days analysis
        Map<String, Long> dayOfWeekStats = allLeaves.stream()
            .collect(Collectors.groupingBy(
                l -> l.getStartDate().getDayOfWeek().toString(),
                Collectors.counting()
            ));
        trends.put("dayOfWeekStats", dayOfWeekStats);

        // Leave type distribution
        Map<String, Long> leaveTypeStats = allLeaves.stream()
            .collect(Collectors.groupingBy(EmployeeLeave::getLeaveType, Collectors.counting()));
        trends.put("leaveTypeStats", leaveTypeStats);

        return trends;
    }

    // Get detailed employee analysis
    public Map<String, Object> getEmployeeAnalysis(String employeeName) {
        List<EmployeeLeave> employeeLeaves = leaveRepository.findByEmployeeName(employeeName);
        Map<String, Object> analysis = new HashMap<>();

        if (employeeLeaves.isEmpty()) {
            analysis.put("message", "No leave history found for this employee");
            return analysis;
        }

        // Basic stats
        long approved = employeeLeaves.stream().filter(l -> "Approved".equals(l.getStatus())).count();
        long rejected = employeeLeaves.stream().filter(l -> "Rejected".equals(l.getStatus())).count();
        long pending = employeeLeaves.stream().filter(l -> "Pending".equals(l.getStatus())).count();

        analysis.put("totalLeaves", employeeLeaves.size());
        analysis.put("approved", approved);
        analysis.put("rejected", rejected);
        analysis.put("pending", pending);
        analysis.put("approvalRate", Math.round((double) approved / employeeLeaves.size() * 100.0) / 100.0);

        // Monthly breakdown
        Map<String, Map<String, Long>> monthlyBreakdown = employeeLeaves.stream()
            .collect(Collectors.groupingBy(
                l -> l.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.groupingBy(EmployeeLeave::getStatus, Collectors.counting())
            ));
        analysis.put("monthlyBreakdown", monthlyBreakdown);

        // Pattern analysis
        Map<String, Object> patterns = analyzePatterns(employeeLeaves, null);
        analysis.put("patterns", patterns);

        // Risk assessment
        List<EmployeeLeave> allLeaves = leaveRepository.findAll();
        Map<String, Object> risks = assessRisks(employeeLeaves, allLeaves, new EmployeeLeave());
        analysis.put("riskProfile", risks);

        return analysis;
    }

    // Get team shortage alerts
    public List<Map<String, Object>> getTeamShortageAlerts() {
        List<EmployeeLeave> allLeaves = leaveRepository.findAll();
        List<Map<String, Object>> alerts = new ArrayList<>();

        // Check next 7 days for potential shortages
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate checkDate = today.plusDays(i);

            long leavesOnDate = allLeaves.stream()
                .filter(l -> ("Approved".equals(l.getStatus()) || "Pending".equals(l.getStatus())))
                .filter(l -> !checkDate.isBefore(l.getStartDate()) && !checkDate.isAfter(l.getEndDate()))
                .count();

            if (leavesOnDate > 2) { // Threshold for alert
                Map<String, Object> alert = new HashMap<>();
                alert.put("date", checkDate.toString());
                alert.put("leavesCount", leavesOnDate);
                alert.put("severity", leavesOnDate > 4 ? "CRITICAL" : "WARNING");
                alert.put("message", String.format("%d employees on leave on %s", leavesOnDate, checkDate.getDayOfWeek()));
                alerts.add(alert);
            }
        }

        return alerts;
    }

    // Get HR alerts for high-risk employees
    public List<Map<String, Object>> getHRAlerts() {
        List<EmployeeLeave> allLeaves = leaveRepository.findAll();
        Map<String, List<EmployeeLeave>> leavesByEmployee = allLeaves.stream()
            .collect(Collectors.groupingBy(EmployeeLeave::getEmployeeName));

        List<Map<String, Object>> alerts = new ArrayList<>();

        for (Map.Entry<String, List<EmployeeLeave>> entry : leavesByEmployee.entrySet()) {
            String employeeName = entry.getKey();
            List<EmployeeLeave> employeeLeaves = entry.getValue();

            // Check for high usage
            if (employeeLeaves.size() > 8) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("employeeName", employeeName);
                alert.put("alertType", "HIGH_USAGE");
                alert.put("totalLeaves", employeeLeaves.size());
                alert.put("message", String.format("%s has %d total leaves - monitor closely", employeeName, employeeLeaves.size()));
                alerts.add(alert);
            }

            // Check for frequent rejections
            long rejections = employeeLeaves.stream().filter(l -> "Rejected".equals(l.getStatus())).count();
            if (rejections > employeeLeaves.size() / 2) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("employeeName", employeeName);
                alert.put("alertType", "FREQUENT_REJECTIONS");
                alert.put("rejectionRate", Math.round((double) rejections / employeeLeaves.size() * 100.0) / 100.0);
                alert.put("message", String.format("%s has %.1f%% rejection rate", employeeName, (double) rejections / employeeLeaves.size() * 100));
                alerts.add(alert);
            }

            // Check for suspicious patterns
            Map<String, Object> patterns = analyzePatterns(employeeLeaves, null);
            if ((Boolean) patterns.get("isPatternDetected")) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("employeeName", employeeName);
                alert.put("alertType", "SUSPICIOUS_PATTERN");
                alert.put("pattern", patterns.get("patternType"));
                alert.put("message", String.format("%s shows %s pattern", employeeName, patterns.get("patternType")));
                alerts.add(alert);
            }
        }

        return alerts;
    }
}