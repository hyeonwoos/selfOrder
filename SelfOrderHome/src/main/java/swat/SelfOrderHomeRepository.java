package swat;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SelfOrderHomeRepository extends CrudRepository<SelfOrderHome, Long> {

	List<SelfOrderHome> findByOrderId(Long orderId);
}