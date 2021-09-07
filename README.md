# Burger God 셀프오더(Self Order)
# 서비스 시나리오
### 기능적 요구사항
1. 고객이 메뉴를 선택하여 주문한다.
2. 고객이 결제를 한다.
3. 결제가 완료되면 주문 내역이 매장으로 전달된다.
4. 매장에서 주문을 할당받아 메뉴를 제조한다.
5. 고객이 주문을 취소할 수 있다.
6. 고객이 중간중간 주문상태를 조회한다.

### 비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지않으면 주문이 진행되지 않는다 → Sync 호출
1. 장애격리
    1. 결제시스템에서 장애가 발생해도 주문취소는 24시간 받을 수 있어야한다 → Async (event-driven), Eventual Consistency
    1. 주문량이 많아 결제시스템 과중되면 잠시 주문을 받지 않고 잠시후에 하도록 유도한다 → Circuit breaker, fallback
1. 성능
    1. 고객이 주문상태를 SelfOrderHome에서 확인 할 수 있어야 한다. → CQRS 

# Event Storming 결과

![EventStorming_V1](https://user-images.githubusercontent.com/49510466/132094172-f2bc2eb8-fedc-45f0-a7d4-fc5a74fdd518.png)

# 부적격 이벤트 탈락

![부적격 이벤트 탈락](https://user-images.githubusercontent.com/49510466/131070824-5da92df1-dc82-40a5-ac09-5bdc0e665db7.PNG)


# 헥사고날 아키텍처 다이어그램 도출

![헥사고날 아키텍처 다이어그램도출](https://user-images.githubusercontent.com/49510466/132094517-c4e6549d-fcd0-41cc-b70b-8861d34681f5.png)


# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084, 8088 이다)
```
cd SelfOrder
mvn spring-boot:run  

cd Payment
mvn spring-boot:run

cd SelfOrderHome
mvn spring-boot:run 

cd Store
mvn spring-boot:run  

cd gateway
mvn spring-boot:run  
```

## DDD(Domain Driven Design) 의 적용
msaez.io 를 통해 구현한 Aggregate 단위로 Entity 를 선언 후, 구현을 진행하였다.

Entity Pattern 과 Repository Pattern 을 적용하기 위해 Spring Data REST 의 RestRepository 를 적용하였다.

**SelfOrder 서비스의 SelfOrder.java**

```java 
package swat;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import swat.external.Payment;
import swat.external.PaymentService;

import java.util.List;

@Entity
@Table(name="SelfOrder_table")
public class SelfOrder {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String userId;
    private String menuId;
    private Integer qty;
    private String status;

    @PostPersist
    public void onPostPersist(){
    	Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        Payment payment = new Payment();
        payment.setOrderId(this.getId());
        payment.setMenuId(this.menuId);
        payment.setQty(this.getQty());
        payment.setUserId(this.getUserId());
        // mappings goes here
        SelfOrderApplication.applicationContext.getBean(PaymentService.class)
        .pay(payment);
    }

    @PostUpdate
    public void onPostUpdate(){
        Updated updated = new Updated();
        BeanUtils.copyProperties(this, updated);
        updated.publishAfterCommit();


    }

    @PreRemove
    public void onPreRemove(){
        OrderCancelled orderCancelled = new OrderCancelled();
        BeanUtils.copyProperties(this, orderCancelled);
        orderCancelled.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getMenuId() {
        return menuId;
    }

    public void setMenuId(String menuId) {
        this.menuId = menuId;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
}
```

**SelfOrder 서비스의 PolicyHandler.java**
```java
package swat;

import swat.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }
    
    @Autowired
	SelfOrderRepository selfOrderRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverAssigned_(@Payload Assigned assigned){

        if(assigned.isMe()){
        	Optional<SelfOrder> optional = selfOrderRepository.findById(assigned.getOrderId());
        	if(optional != null && optional.isPresent())
        	{
        		SelfOrder selfOrder = optional.get();
        		
        		selfOrder.setStatus("Assigned");
                // view 객체에 이벤트의 eventDirectValue를 set함
                // view 레파지토리에 save
            	selfOrderRepository.save(selfOrder);
        	}
            
            System.out.println("##### listener  : " + assigned.toJson());
        }
    }

}
```

- DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.  
  
- 원격 주문 (SelfOrder 동작 후 결과)

![image](https://user-images.githubusercontent.com/49510466/132097680-69515d92-08c6-43de-9ff1-c7accf634273.png)

# GateWay 적용
API GateWay를 통하여 마이크로 서비스들의 진입점을 통일할 수 있다.
다음과 같이 GateWay를 적용하였다.

```yaml
server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: SelfOrder
          uri: http://localhost:8081
          predicates:
            - Path=/selfOrders/** 
        - id: Payment
          uri: http://localhost:8082
          predicates:
            - Path=/payments/** 
        - id: Store
          uri: http://localhost:8083
          predicates:
            - Path=/stores/** 
        - id: SelfOrderHome
          uri: http://localhost:8084
          predicates:
            - Path= /selfOrderHomes/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: SelfOrder
          uri: http://SelfOrder:8080
          predicates:
            - Path=/selfOrders/** 
        - id: Payment
          uri: http://Payment:8080
          predicates:
            - Path=/payments/** 
        - id: Store
          uri: http://Store:8080
          predicates:
            - Path=/stores/** 
        - id: SelfOrderHome
          uri: http://SelfOrderHome:8080
          predicates:
            - Path= /selfOrderHomes/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080

```

# CQRS
Materialized View 를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이) 도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다.
본 프로젝트에서 View 역할은 SelfOrderHomes 서비스가 수행한다.

- 주문(ordered) 실행 후 SelfOrderHomes 화면

![image](https://user-images.githubusercontent.com/49510466/132097760-7779c5b3-ae23-4ab3-86c7-3822684692ab.png)

- 주문(OrderCancelled) 취소 후 SelfOrderHomes 화면

![image](https://user-images.githubusercontent.com/49510466/132099074-98691d0b-e6a7-4045-8845-25e9544ffd02.png)

위와 같이 주문을 하게되면 SelfOrder -> Payment -> Store -> SelfOrderHome로 주문이 처리되어 Assigned 되고

주문 취소가 되면 Status가 refunded로 Update 되는 것을 볼 수 있다.

또한 Correlation을 key를 활용하여 orderId를 Key값으로 하고 원하는 주문하고 서비스간의 공유가 이루어지도록 하였다.

위의 결과로 서로 다른 마이크로서비스 간에 트랜잭션이 묶여 있음을 알 수 있다.

# 폴리글랏

Store 서비스의 DB와 SelfOrder의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.

**Store의 pom.xml DB 설정 코드**

![image](https://user-images.githubusercontent.com/49510466/132099264-c8d4b045-9512-41ea-b18e-823ed7cc6b26.png)

**SelfOrder의 pom.xml DB 설정 코드**

![image](https://user-images.githubusercontent.com/49510466/132099246-4c0ff067-8c38-4019-8392-a914932dfb98.png)

# 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(SelfOrder)->결제(pay) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하도록 한다.

**SelfOrder 서비스 내 external.PaymentService**
```java
package swat.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="Payment", url="${api.url.Payment}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void pay(@RequestBody Payment payment);

}
```

**동작 확인**
- 잠시 Payment 서비스 중지시킴

![image](https://user-images.githubusercontent.com/49510466/132099536-3fd66a28-05af-4637-938e-969347405fb8.png)

- 주문 요청 시 에러 발생

![image](https://user-images.githubusercontent.com/49510466/132099391-47052640-cf74-498a-a9c3-f624d1d52adf.png)

- Payment 서비스 재기동 후 정상동작 확인

![image](https://user-images.githubusercontent.com/49510466/132099588-b383b493-9fb7-493a-bb9a-0df42a40e8ae.png)
![image](https://user-images.githubusercontent.com/49510466/132099666-5b11b2ed-5345-46af-9c49-33f6ebb78a26.png)

# 운영

# Deploy / Pipeline

- git에서 소스 가져오기
```
git clone https://github.com/hyeonwoos/selfOrder.git
```
- Build 하기
```
cd /swat
cd gateway
mvn package

cd ..
cd SelfOrder
mvn package

cd ..
cd Payment
mvn package

cd ..
cd Store
mvn package

cd ..
cd SelfOrderHome
mvn package
```

- Docker Image Push/deploy/서비스생성
```
cd gateway
docker build -t hyeonwoo.azurecr.io/gateway:v1 .
docker push hyeonwoo.azurecr.io/gateway:v1

kubectl create ns tutorial
kubectl create deploy gateway --image=hyeonwoo.azurecr.io/gateway:v1 -n tutorial
kubectl expose deploy gateway --type=ClusterIP --port=8080 -n tutorial

kubectl create deploy selforder --image=hyeonwoo.azurecr.io/selforder:v1 -n tutorial
kubectl expose deploy selforder --type=ClusterIP --port=8080 -n tutorial

cd ..
cd Payment
docker build -t hyeonwoo.azurecr.io/payment:v1 .
docker push hyeonwoo.azurecr.io/payment:v1

kubectl create deploy payment --image=hyeonwoo.azurecr.io/payment:v1 -n tutorial
kubectl expose deploy payment --type=ClusterIP --port=8080 -n tutorial

cd ..
cd Store
docker build -t hyeonwoo.azurecr.io/store:v1 .
docker push hyeonwoo.azurecr.io/store:v1

kubectl create deploy store --image=hyeonwoo.azurecr.io/store:v1 -n tutorial
kubectl expose deploy store --type=ClusterIP --port=8080 -n tutorial

cd ..
cd SelfOrderHome
docker build -t hyeonwoo.azurecr.io/selforderhome:v1 .
docker push hyeonwoo.azurecr.io/selforderhome:v1

kubectl create deploy selforderhome --image=hyeonwoo.azurecr.io/selforderhome:v1 -n tutorial
kubectl expose deploy selforderhome --type=ClusterIP --port=8080 -n tutorial
```

- yml파일 이용한 deploy
```
cd ..
cd  SelfOrder
docker build -t hyeonwoo.azurecr.io/selforder:v1 .
docker push hyeonwoo.azurecr.io/selforder:v1
```
![image](https://user-images.githubusercontent.com/49510466/132284749-c7529a99-33c6-4cc7-abf8-936bf99baa6f.png)

```
kubectl expose deploy store --type=ClusterIP --port=8080 -n tutorial
```

- swat/SelfOrder/kubernetes/deployment.yml 파일 
```yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: selforder
  namespace: tutorial
  labels:
    app: selforder
spec:
  replicas: 1
  selector:
    matchLabels:
      app: selforder
  template:
    metadata:
      labels:
        app: selforder
    spec:
      containers:
        - name: selforder
          image: hyeonwoo.azurecr.io/selforder:v1
          ports:
            - containerPort: 8080
          env:
            - name: configurl
              valueFrom:
                configMapKeyRef:
                  name: apiurl
                  key: url
```	  
- deploy 완료

![image](https://user-images.githubusercontent.com/49510466/132284564-666caded-380a-457b-a6f4-fd0e7ea58e93.png)

![image](https://user-images.githubusercontent.com/49510466/132284859-1038a50b-cf4d-4366-afad-c749dd755727.png)

![image](https://user-images.githubusercontent.com/49510466/132285265-fdfe24a9-329d-45d9-984f-e6beb1157147.png)

# ConfigMap 
- 시스템별로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리

- application.yml 파일에 ${configurl} 설정

```yaml
      feign:
        hystrix:
          enabled: true
      hystrix:
        command:
          default:
            execution.isolation.thread.timeoutInMilliseconds: 610
      api:
        url:
          Payment: ${configurl}

```

- ConfigMap 사용(/SelfOrder/src/main/java/swat/external/PaymentService.java) 

```java

      @FeignClient(name="Payment", url="${api.url.Payment}")
      public interface PaymentService {
      
	      @RequestMapping(method= RequestMethod.POST, path="/payments")
              public void pay(@RequestBody Payment payment);
	      
      }
```

- Deployment.yml 에 ConfigMap 적용

![image](https://user-images.githubusercontent.com/49510466/132286415-17f4dc80-818c-4d1c-926f-8b9a13a636bc.png)

- ConfigMap 생성

```
kubectl create configmap apiurl --from-literal=url=http://10.0.182.34 -n tutorial
```


# 오토스케일 아웃

- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

>- 단, 부하가 제대로 걸리기 위해서, recipe 서비스의 리소스를 줄여서 재배포한다.(swat/Store/kubernetes/deployment.yml 수정)

```yaml
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```

- 다시 expose 해준다.
```
kubectl expose deploy store --type=ClusterIP --port=8080 -n tutorial
```
- recipe 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy store --min=1 --max=10 --cpu-percent=15 -n tutorial
```
- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it pod/siege -c siege -n tutorial -- /bin/bash
siege -c100 -t120S -r10 -v --content-type "application/json" 'http://20.200.207.111:8080/stores POST {"orderId": 111, "userId": "smith", "menuId": "whopper", "qty":10}'
```
![image](https://user-images.githubusercontent.com/49510466/132291288-756d5a60-a564-49e6-ad0f-62b9de9b1ba4.png)

- 오토스케일 모니터링을 걸어 스케일 아웃이 자동으로 진행됨을 확인한다.
```
kubectl get all -n tutorial
```
![image](https://user-images.githubusercontent.com/49510466/132290975-023bc79c-c93b-4f81-b9b8-a12e4bc35a4d.png)

# 서킷 브레이킹

- 서킷 브레이킹 프레임워크의 선택 : Spring FeignClient + Hystrix 옵션을 사용하여 구현함
- Hystrix를 설정 : 요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도
  유지되면 CB 회로가 닫히도록(요청을 빠르게 실패처리, 차단) 설정

- 동기 호출 주체인 SelfOrder에서 Hystrix 설정 
- SelfOrder/src/main/resources/application.yml 파일
```yaml
feign:
  hystrix:
    enabled: true
hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610
```

- 부하에 대한 지연시간 발생코드
- swat/SelfOrder/src/main/java/swat/external/PaymentService.java
``` java
    @PostPersist
    public void onPostPersist(){
        Payed payed = new Payed();
        BeanUtils.copyProperties(this, payed);
        payed.publishAfterCommit();
        
        try {
                Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
                e.printStackTrace();
        }
    }
```

- 부하 테스터 siege툴을 통한 서킷 브레이커 동작확인 :
  
  동시 사용자 100명, 60초 동안 실시 
```
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://20.200.207.111:8080/selfOrders POST {"userId": "smith", "menuId": "whopper", "qty":10}'
```
- 부하 발생하여 CB가 발동하여 요청 실패처리하였고, 밀린 부하가 다시 처리되면서 SelfOrders를 받기 시작
 
![image](https://user-images.githubusercontent.com/49510466/132291976-b1fa0555-0a79-4879-b24d-aa791b2759fd.png)

# 무정지 배포(Zero-downtime deploy (readiness probe))

- 무정지 배포를 위한 readiness 옵션 설정 적용
swat/Store/kubernetes/deployment.yml
```yml
    spec:
      containers:
        - name: store
          image: hyeonwoo.azurecr.io/store:v1
          ports:
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

- 무정지 배포를 위한 readiness 옵션 설정 후 적용 시 Siege 결과 Availability가 100% 확인

- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it pod/siege -c siege -n tutorial -- /bin/bash
siege -c100 -t120S -r10 -v --content-type "application/json" 'http://20.200.207.111:8080/stores POST {"orderId": 111, "userId": "smith", "menuId": "whopper", "qty":10}'
```
먼저 store 이미지가 v1 임을 확인하고 readiness 설정이 적용되어 있음을 확인
![image](https://user-images.githubusercontent.com/49510466/132306379-640b09b8-4e2b-4978-ae40-3bcc135cb908.png)

새 버전으로 배포(이미지를 v2으로 변경)
```
kubectl set image deployment store store=hyeonwoo.azurecr.io/store:v2 -n tutorial
```
![image](https://user-images.githubusercontent.com/49510466/132306493-8cc783d3-029e-4555-bc11-853579dbae65.png)

store 이미지가 v2 임을 확인
![image](https://user-images.githubusercontent.com/49510466/132306521-610529b5-8421-4771-8193-e276404fa228.png)

Siege 결과 Availability가 100% 확인

![image](https://user-images.githubusercontent.com/49510466/132308384-6b6c73ae-cba0-4050-927f-4b51bbb75a36.png)


# Self-healing (Liveness Probe)

- Self-healing 확인을 위한 Liveness Probe 옵션 변경
![image](https://user-images.githubusercontent.com/49510466/131082754-fbc0327b-18e7-498f-ae5c-030ab1112b09.png)

swat/Store/kubernetes/deployment_live.yml
```yml
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8081
            initialDelaySeconds: 5
            periodSeconds: 5
```

- Store pod에 Liveness Probe 옵션 적용 확인

![image](https://user-images.githubusercontent.com/49510466/132307371-1f199e71-2a13-4eb7-bb0c-15f5d4249ebd.png)

- Store pod에서 적용 시 retry발생 확인

![image](https://user-images.githubusercontent.com/49510466/132308272-20491c4c-84d4-4f08-bca5-a47758bba096.png)

![image](https://user-images.githubusercontent.com/49510466/132307921-51657cbe-97ee-4f76-a161-f6d3c71183be.png)

![image](https://user-images.githubusercontent.com/49510466/132308021-e2af89a2-18a5-412a-ad2c-30e061aa39f7.png)


