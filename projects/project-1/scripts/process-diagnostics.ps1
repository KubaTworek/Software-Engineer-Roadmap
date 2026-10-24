param([Parameter(Mandatory)][string]$Container)
$ErrorActionPreference = 'Stop'
# Odczyt tylko z nazwanej repliki Booking. Błąd polecenia nie oznacza zdrowej aplikacji.
docker stats --no-stream $Container
if ($LASTEXITCODE -ne 0) { throw 'Nie można odczytać statystyk repliki Booking' }
docker inspect --format '{{json .State}}' $Container
docker exec $Container sh -ec 'cat /proc/1/status; cat /proc/1/limits; if [ -f /sys/fs/cgroup/memory.current ]; then cat /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.events /sys/fs/cgroup/cpu.stat; else cat /sys/fs/cgroup/memory/memory.usage_in_bytes /sys/fs/cgroup/memory/memory.limit_in_bytes /sys/fs/cgroup/memory/memory.oom_control /sys/fs/cgroup/cpu/cpu.stat; fi'
if ($LASTEXITCODE -ne 0) { throw 'Brak pełnego pomiaru Linux/cgroup' }
Write-Output 'Porównaj VmRSS z heapem z JFR/JMX; memory.current obejmuje szerszy zakres cgroup niż sam proces JVM.'
