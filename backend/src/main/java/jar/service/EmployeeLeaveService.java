package jar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import jar.entity.EmployeeLeave;
import jar.repository.EmployeeLeaveRepository;

@Service
public class EmployeeLeaveService {

    @Autowired
    private EmployeeLeaveRepository repository;

    // CREATE
    public EmployeeLeave createLeave(EmployeeLeave leave) {
        leave.setStatus("Pending"); // default status
        return repository.save(leave);
    }

    // READ
    public List<EmployeeLeave> getAllLeaves() {
        return repository.findAll();
    }

    public List<EmployeeLeave> getLeavesByEmployeeName(String employeeName) {
        return repository.findByEmployeeName(employeeName);
    }

    // UPDATE
    public EmployeeLeave updateLeave(Long id, EmployeeLeave leaveDetails) {
        EmployeeLeave existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave not found with id: " + id));

        existing.setEmployeeName(leaveDetails.getEmployeeName());
        existing.setLeaveType(leaveDetails.getLeaveType());
        existing.setStartDate(leaveDetails.getStartDate());
        existing.setEndDate(leaveDetails.getEndDate());
        existing.setReason(leaveDetails.getReason());

        if (leaveDetails.getStatus() != null) {
            existing.setStatus(leaveDetails.getStatus());
        }

        return repository.save(existing);
    }

    // DELETE
    public void deleteLeave(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Leave not found with id: " + id);
        }
        repository.deleteById(id);
    }
}