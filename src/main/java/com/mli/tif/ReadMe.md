#### Swagger Ui
- http://localhost:12000/swagger-ui/index.html

#### 資料結構

```
contract                              # 定義類的 interface (為了要自動啟動 非同步，需要統一規則)
  ├── DataCalcContract.java             # 數據計算 方法定義
  ├── RuleContract.java                 # 規則判斷 方法定義
controller                            # 處理請求
service                               # 業務邏輯
  ├── MainProcessService.java           # 主流程
  ├── RuleTableService.java             # 規則表
  ├── datacalc                          # 數據計算方法 (裡面的方法要繼承 DataCalcContract)
     ├── ...                              # 各種需要計算的欄位 (一個欄位，一個檔案)
  ├── ruleeval                          # 規則判斷方法 (裡面的方法要繼承 RuleContract)
     ├── ...                              # 各種規則檢核設定 (一個可以獨立作業的規則，一個檔案)
     ├── SpELCheckService.java            # spEL 的 共用檢核規則
  ├── helpers                           # 提供輔助判斷的共用方法庫
     ├── ...                              # 依照業務邏輯進行拆分
  ├── spelcalc                          # spEL 數據計算方法 (裡面的方法要繼承 SpELCalcContract)
     ├── ...                              # 各種需要計算的欄位 (一組獨立判斷的欄位，一個檔案)
dto                                   # 一般的資料傳輸物件
vo                                    # 對外的資料傳輸物件
```