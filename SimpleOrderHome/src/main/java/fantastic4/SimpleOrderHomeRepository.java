package fantastic4;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SimpleOrderHomeRepository extends CrudRepository<SimpleOrderHome, Long> {

	List<SimpleOrderHome> findByOrderId(Long orderId);
}