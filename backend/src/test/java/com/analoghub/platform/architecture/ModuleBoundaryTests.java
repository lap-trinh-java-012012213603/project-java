package com.analoghub.platform.architecture;

import com.analoghub.platform.AnaloghubBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundaryTests {

    @Test
    void verifiesModularStructure() {
        // Quét toàn bộ class bắt đầu từ package gốc
        ApplicationModules modules = ApplicationModules.of(AnaloghubBackendApplication.class);
        
        // In ra danh sách các module phát hiện được để bạn dễ theo dõi trên terminal
        System.out.println("--- DANH SÁCH MODULE ĐƯỢC PHÁT HIỆN ---");
        modules.forEach(System.out::println);
        System.out.println("---------------------------------------");
        
        // Kiểm tra kiến trúc: Đảm bảo DAG Zero Cycles và tính đóng gói (Encapsulation)
        modules.verify();
    }
}