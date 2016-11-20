# photoWall
simple demo of photowall


##写了什么
- 用RecyclerView实现
    - 这个以前写过，就写了个回调，其它的都是面向百度的，嗯。。
- 自己封装网络请求，请使用三级缓存
    - 这里为主要难点，耗时最长，用的LruCache 和 DiskLruCache ，还有图片的各种加载啊，压缩啊，inputStream不能被decode两次啊。。。遇到的困难也最多
    这些功能我都写在了adapter中，耦合较高，以后再把它解耦写个工具类吧。
- 滑动流畅
    - 这里只是用了onScrollListener做了个监听事件，滑动的时候不加载，停下来后进行加载，每次加载完成后notifyDataSetChange 一下
- 下拉刷新，展示更多图片
    - 这个也比较简单，也是用onScrollListener实现的，判断是否滑到了最后一个item
        但是，这里要实现这种功能的话，只能用LinearLayoutManager，不能用瀑布流，用瀑布流得自己改下方法，
        根据x，y的滚动情况，和每个子view的高来判断是否滑到底了，想想就麻烦。。还是先把该做的做了吧
    - 判断到最后一个item了之后，在没有滑动的情况下，再次加载下一页api，把得到的arraylist添加到数据源datas的末尾
- 点击图片进入看图模式，左右滑切换图片
    - 这里我用的是viewpager，直接新开一个Activity， 将viewpager的子项设置为ImageView，在这个Activity也进行了三级缓存
    （这里问题比较大，我的三级缓存应该是用于整个app的吧？我要怎么写？我这里相当于又建立了一套三级缓存）
- 请注意代码的规范（代码太烂看着脑壳痛o(╯□╰)o，照顾一下老人家）
    - 军哥老了？没有吧？那我们写烂点吧，锻炼下老人家的眼力，脑力，思维力，脾气控制力...多好啊~~~
