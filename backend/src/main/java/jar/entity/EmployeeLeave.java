package jar.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeName;

    private String leaveType;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String status;
}