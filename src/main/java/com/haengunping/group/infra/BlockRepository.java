package com.haengunping.group.infra;
import com.haengunping.group.domain.Block;
import com.haengunping.group.domain.BlockId;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BlockRepository extends JpaRepository<Block,BlockId> {}
