package jar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import jar.entity.EmployeeLeave;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {
    List<EmployeeLeave> findByEmployeeName(String employeeName);
}