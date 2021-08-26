package fantastic4;

import fantastic4.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class SimpleOrderHomeViewHandler {


    @Autowired
    private SimpleOrderHomeRepository simpleOrderHomeRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPayed_then_CREATE_1 (@Payload Payed payed) {
        try {
            if (payed.isMe()) {
            	// view 객체 생성
            	SimpleOrderHome simpleOrderHome = new SimpleOrderHome();
                // view 객체에 이벤트의 Value 를 set 함
            	simpleOrderHome.setOrderId(payed.getOrderId());
            	simpleOrderHome.setUserId(payed.getUserId());
            	simpleOrderHome.setMenuId(payed.getMenuId());
            	simpleOrderHome.setPayId(payed.getId());
            	simpleOrderHome.setQty(payed.getQty());
            	simpleOrderHome.setStatus("Payed");
                // view 레파지 토리에 save
            	simpleOrderHomeRepository.save(simpleOrderHome);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenAssigned_then_UPDATE_1(@Payload Assigned assigned) {
        try {
            if (assigned.isMe()) {
                // view 객체 조회
            	List<SimpleOrderHome> simpleOrderHomeList = simpleOrderHomeRepository.findByOrderId(assigned.getOrderId());
                for(SimpleOrderHome simpleOrderHome : simpleOrderHomeList){
                	simpleOrderHome.setStoreId(assigned.getId());
                	simpleOrderHome.setStatus("Assigned");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	simpleOrderHomeRepository.save(simpleOrderHome);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderCancelled_then_UPDATE_2(@Payload OrderCancelled orderCancelled) {
        try {
            if (orderCancelled.isMe()) {
                // view 객체 조회
            	List<SimpleOrderHome> simpleOrderHomeList = simpleOrderHomeRepository.findByOrderId(orderCancelled.getId());
            	for(SimpleOrderHome simpleOrderHome : simpleOrderHomeList){
                	simpleOrderHome.setStoreId(orderCancelled.getId());
                	simpleOrderHome.setStatus("Refunded");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	simpleOrderHomeRepository.save(simpleOrderHome);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenRefunded_then_DELETE_1(@Payload Refunded refunded) {
        try {
            if (refunded.isMe()) {
            	List<SimpleOrderHome> simpleOrderHomeList = simpleOrderHomeRepository.findByOrderId(refunded.getOrderId());
                for(SimpleOrderHome simpleOrderHome : simpleOrderHomeList){
                	simpleOrderHome.setStoreId(refunded.getId());
                	simpleOrderHome.setStatus("Refunded");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	simpleOrderHomeRepository.save(simpleOrderHome);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}