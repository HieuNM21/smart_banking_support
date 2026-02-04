package com.example.smart_banking_support.repository;

import com.example.smart_banking_support.entity.TicketAIInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketAIInsightRepository extends JpaRepository<TicketAIInsight, Long> {

}